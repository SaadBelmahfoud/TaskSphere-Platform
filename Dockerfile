# ═══════════════════════════════════════════════════════════════════
# DOCKERFILE MULTI-STAGE — TaskSphere Platform
# ═══════════════════════════════════════════════════════════════════
#
# PRINCIPE DU MULTI-STAGE BUILD :
# ────────────────────────────────
# On utilise DEUX étapes (stages) dans le Dockerfile :
#
# STAGE 1 (build) : Image avec Maven + JDK → compile le projet
# STAGE 2 (run)   : Image légère avec JRE seulement → exécute le JAR
#
# Pourquoi ? L'image finale ne contient PAS Maven, PAS le code source,
# PAS les fichiers temporaires → image beaucoup plus petite.
#
# Taille typique :
# - Stage 1 (build) : ~500 MB
# - Stage 2 (run)   : ~200 MB (3x plus petit !)
#
# SÉCURITÉ : L'image finale ne contient PAS le code source Java.
# Même si quelqu'un accède au conteneur, il ne peut pas lire le code.
#
# ═══════════════════════════════════════════════════════════════════
# CORRECTION SPRINT 5 — Utilisation de l'image Maven officielle
# ═══════════════════════════════════════════════════════════════════
#
# AVANT : apk add --no-cache maven
#   → Version Maven non contrôlée (celle du dépôt Alpine)
#   → Risque d'incompatibilité avec le projet
#   → Pas de cache Maven entre les builds
#
# APRÈS : Image maven:3.9-eclipse-temurin-17
#   → Version Maven contrôlée et compatible avec Java 17
#   → Cache Maven optimisé (volume .m2)
#   → Image officielle maintenue par Docker Hub

# ═══════════════════════════════════════════════════════
# STAGE 1 : BUILD — Compiler le projet avec Maven
# ═══════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-17 AS build

# Répertoire de travail dans le conteneur
WORKDIR /app

# Copier les fichiers Maven en PREMIER (pour le cache Docker)
# PRINCIPE DU CACHE DOCKER :
# Chaque instruction CREATE un "layer". Si le layer n'a pas changé,
# Docker le réutilise sans le reconstruire.
# En copiant pom.xml AVANT le code source, Maven télécharge les
# dépendances une seule fois (tant que pom.xml ne change pas).
COPY pom.xml .
COPY tasksphere-iam/pom.xml tasksphere-iam/
COPY tasksphere-core/pom.xml tasksphere-core/

# Télécharger les dépendances Maven (cache layer)
# -B = batch mode (pas de progression interactive)
# Ce layer est reconstruit UNIQUEMENT si pom.xml change.
RUN mvn dependency:go-offline -B

# Copier le code source
COPY . .

# Compiler le projet et créer le JAR
# -DskipTests : on saute les tests dans le Docker build
# (les tests sont exécutés dans le CI/CD avant le build Docker)
RUN mvn clean package -DskipTests -B

# ═══════════════════════════════════════════════════════
# STAGE 2 : RUN — Exécuter le JAR avec JRE uniquement
# ═══════════════════════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine

# Métadonnées de l'image
LABEL maintainer="TaskSphere Team"
LABEL description="TaskSphere Platform - Task Management Application"

# Créer un utilisateur non-root pour la sécurité
# PRINCIPE : Ne JAMAIS exécuter une application en tant que root
# dans un conteneur Docker. Si l'application est compromise,
# l'attaquant n'a pas les droits root.
RUN addgroup -S tasksphere && adduser -S tasksphere -G tasksphere

WORKDIR /app

# Copier le JAR depuis le stage de build
COPY --from=build /app/tasksphere-core/target/*.jar app.jar

# Changer le propriétaire du fichier
RUN chown tasksphere:tasksphere app.jar

# ═══════════════════════════════════════════════════════════════════
# PHASE 3 — CORRECTION UPLOAD : Créer le répertoire de stockage
# ═══════════════════════════════════════════════════════════════════
#
# AVANT (CRITIQUE) :
#   Aucun répertoire /app/uploads n'était créé.
#   L'utilisateur non-root "tasksphere" ne pouvait PAS créer
#   /app/uploads/attachments car /app est propriété de root.
#   → Files.createDirectories() → AccessDeniedException
#   → "Échec du stockage du fichier"
#
# APRÈS :
#   On crée /app/uploads/attachments AVANT le USER tasksphere
#   et on donne la propriété à l'utilisateur tasksphere.
#   Le runtime peut alors écrire dans ce répertoire.
#
# L'instruction RUN s'exécute en tant que root (avant USER tasksphere).
# Le chown -R donne la propriété récursive du répertoire uploads.
# ═══════════════════════════════════════════════════════════════════
RUN mkdir -p /app/uploads/attachments && \
    chown -R tasksphere:tasksphere /app/uploads

# Utiliser l'utilisateur non-root
USER tasksphere

# Port exposé par l'application
EXPOSE 8080

# ═══════════════════════════════════════════════════════════════════
# PHASE 3 — FEATURE 6 : Volume pour le stockage des uploads
# ═══════════════════════════════════════════════════════════════════
# Déclare /app/uploads comme volume Docker.
# Cela permet de monter un volume hôte ou un volume nommé pour
# persister les fichiers uploadés entre les redémarrages du conteneur.
# Sans volume, les fichiers sont perdus quand le conteneur est recréé.
# ═══════════════════════════════════════════════════════════════════
VOLUME /app/uploads

# Variables d'environnement par défaut
# Elles peuvent être surchargées au démarrage du conteneur
ENV SPRING_PROFILES_ACTIVE=postgres
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check : vérifie que l'application répond
# PRINCIPE : Docker exécute cette commande périodiquement.
# Si elle échoue 3 fois de suite → le conteneur est marqué "unhealthy".
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Commande de démarrage
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]