***

# 📚 Document de Formation : Les Principes Fondamentaux de TaskSphere Platform
**Version :** 1.0 (Couvrant les V1 à V6)
**Auteur :** Tech Lead
**Audience :** Développeurs juniors, nouveaux arrivants sur le projet.

---

## Introduction

Ce document a pour but d'expliquer **pourquoi** TaskSphere est codée de cette manière, et non **comment** le code est écrit. Nous n'utilisons pas Spring Boot comme un simple "framework magique", mais comme l'implémentation stricte de principes d'architecture éprouvés.

Chaque section correspond à une étape de l'évolution du projet (V1 à V6).

---

## Module 1 : Le Moteur - IoC et Injection de Dépendances (V1)

### Définition
**IoC (Inversion of Contrôle)** : Principe de design où le contrôle de la création des objets est retiré du code applicatif pour être confié à un conteneur externe (Spring).
**DI (Injection de Dépendances)** : Le mécanisme par lequel le conteneur Spring fournit les dépendances dont un objet a besoin pour fonctionner.

### Le Schéma "Avant / Après"

❌ **Sans Spring (Couplage Fort) :**
```text
[ Mon Code ] ---> new TaskManager() ---> [ Dépend de TaskRepository ]
```
*Problème : Si TaskRepository change, je dois casser et recréer Mon Code.*

✅ **Avec Spring IoC (Couplage Faible) :**
```text
                     (Le Conteneur Spring crée tout)
[ Mon Code ] <------- [ TaskManager ] <------- [ TaskRepository ]
      |                   |
      +-------------------+  <-- Spring "injecte" TaskManager dans Mon Code
```

### Ce qu'on a appliqué dans TaskSphere
1.  **Le Pattern Singleton :** Par défaut, Spring ne crée qu'une seule instance de `@Service`. Si 1000 utilisateurs font une requête, ils utilisent le même objet `TaskManager` en mémoire. Gain de RAM énorme.
2.  **Constructor Injection :** On n'utilise jamais `@Autowired` sur un attribut. On met l'attribut `final` et on passe par le constructeur. Pourquoi ? Pour garantir l'immuabilité et pouvoir tester la classe avec un mock sans avoir besoin de lancer Spring.

---

## Module 2 : L'Interface - Spring Web et l'Architecture REST (V2)

### Définition
**REST (Representational State Transfer) :** Style d'architecture où le serveur expose des "Ressources" (nos tâches) identifiées par des URLs, manipulables via les verbes HTTP (`GET` pour lire, `POST` pour créer).

### Le Schéma de séparation (Le Pattern DTO)

Une erreur classique est d'exposer directement sa base de données sur le web. Nous utilisons le **DTO (Data Transfer Object)**.

```text
[ Navigateur/Client ] 
       |  (Envoie JSON)
       v
[ TaskController ] ---> Utilise TaskCreateRequest (DTO Entrant)
       |
       +---> [ TaskManager (Service) ] ---> Utilise Task (Domaine)
       |
       v  (Renvoie JSON)
[ TaskController ] ---> Utilise TaskResponse (DTO Sortant)
```

### Ce qu'on a appliqué dans TaskSphere
*   **Pourquoi les DTO ?** Si demain on ajoute un champ `hashedPassword` dans notre objet Métier (`Task`), le DTO `TaskResponse` ne l'aura pas. Le mot de passe ne fuit donc jamais sur internet. Le Domaine est protégé.

---

## Module 3 : La Mémoire - Spring Data JPA (V3)

### Définition
**JPA (Java Persistence API) :** Une spécification qui permet de mapper des objets Java directement sur des tables d'une base de données relationnelle sans écrire de requêtes SQL manuelles (via l'implémentation Hibernate).

### Le Schéma du Mapping

```text
[ Monde Objet (Java) ]                         [ Monde Relationnel (SQL) ]
                                                              |
class TaskEntity {                                     CREATE TABLE tasks (
  String id;           <------ Mapping ------>           id VARCHAR(36),
  String title;                                        title VARCHAR(255),
}                                                       description VARCHAR(255)
                                                              );
```

### Le conflit architecturale (L'Anti-Pattern)
Les objets JPA (`@Entity`) doivent avoir un constructeur vide et des setters (pour qu'Hibernate puisse les créer depuis la BDD). Cela viole le principe d'immuabilité du Domaine (nos `Record` sans setters de la V1).
**Solution mise en place :** On a créé deux objets distincts. `TaskEntity` (technique, mutable) et `Task` (métier, immuable). Le Service fait la traduction.

---

## Module 4 : La Forteresse - Architecture Hexagonale / Clean Arch (V4)

### Définition
**Architecture Hexagonale (Ports et Adaptateurs) :** L'objectif est d'isoler la logique métier (le Domaine) des technologies (Base de données, Interface Web, API Externes). Le domaine ne doit avoir **aucune dépendance** vers l'extérieur.

### Le Schéma de l'Hexagone

```text
                  [ ADAPTATEUR ENTRANT ]
                  (RestController - Spring Web)
                              |
                              v
[ PORT IN ] -------> [ LE DOMAINE METIER ] <------- [ PORT OUT ]
(Interface)          (Task, TaskManager)               (Interface)
                              ^                               |
                              |                               v
                  [ ADAPTATEUR SORTANT ]           [ ADAPTATEUR SORTANT ]
                  (AuthService - Spring Security)   (JpaAdapter - MySQL/H2)
```

### Ce qu'on a appliqué dans TaskSphere
*   **Les Ports (Interfaces) :** `TaskPersistencePort` (dans le domaine). C'est un contrat.
*   **Les Adaptateurs (Implémentations) :** `TaskPersistenceAdapter` (dans `adapter.out`). C'est lui qui importe JPA.
*   **Le résultat magique :** `TaskManager` ne contient plus aucun `import` vers JPA. Si on change de base de données demain, on change l'Adaptateur, le Service n'est pas modifié.

---

## Module 5 : Le Mur de Sécurité - Spring Security & JWT (V5)

### Définition
**JWT (JSON Web Token) :** Standard d'authentification "Stateless" (sans état). Le serveur ne garde aucune session en mémoire. Il vérifie une signature cryptographique dans le token pour authentifier l'utilisateur.

### Le Schéma de la "Filter Chain" (Chaîne de responsabilité)

Quand une requête HTTP arrive, elle traverse un mur de filtres.

```text
Requête HTTP (GET /api/v1/tasks)
       |
       v
[ Filtre 1 : JwtAuthenticationFilter ]
       |
       |--(Pas de Header Authorization)---> ❌ REJET (401 Unauthorized)
       |
       |--(Header Bearer présent)---------> [ Vérifie la signature cryptographique ]
       |                                         |
       |                                         |--(Signature Invalide)--> ❌ REJET (403 Forbidden)
       |                                         |
       |                                         |--(Signature Valide)--> [ Met l'utilisateur dans le SecurityContext ]
       |                                                                         |
       v                                                                         v
[ Filtre 2 : AuthorizationFilter ] ------------------------------------> [ Autorisé à aller au Controller ]
```

### Le piège de la "Circular Dependency"
Spring Security est complexe. Si `SecurityConfig` a besoin du Filtre, et que le Filtre a besoin de la Config pour trouver les utilisateurs, l'application plante au démarrage (Boucle infinie).
**Solution TaskSphere :** Création de classes `@Configuration` séparées (`UserDetailsConfig` vs `SecurityConfig`) pour briser le cercle.

---

## Module 6 : L'Arrière-Cuisine - Spring Events & Asynchronisme (V6)

### Définition
**Architecture Orientée Événements (EDA) :** Au lieu d'appeler une fonction directement (Appel synchrone bloquant), on "publie" un fait accompli (un Événement). Un ou plusieurs "Écouteurs" réagissent à cet événement de manière détachée.

### Le Schéma Synchrone vs Asynchrone

❌ **Synchrone (V1 à V5 pour les emails par ex.) :**
```text
[ Client ] ---> [ Controller ] ---> [ Service ] ---> [ Envoie Email (3 secondes) ] ---> [ Client reçoit la réponse ]
                                                                                ^
                                                                        LE CLIENT ATTEND 3 SECONDES. EXPÉRIENCE TERRIBLE.
```

✅ **Asynchrone (V6 avec @Async) :**
```text
[ Client ] ---> [ Controller ] ---> [ Service ] ---> [ Publie un Événement ] ---> [ Client reçoit la réponse IMMÉDIATEMENT ]
                                                      |
                                                      v
                                              [ File d'attente Spring ]
                                                      |
                                                      v (Sur un autre Thread/Tache de fond)
                                              [ Écouteur ] ---> [ Envoie Email (3 secondes) ]
```

### Ce qu'on a appliqué dans TaskSphere
1.  **Domain Event :** `TaskCreatedEvent` (Objet immuable contenant les données de l'événement).
2.  **Port Sortant :** `EventPublisherPort` (Pour respecter l'architecture hexagonale, le Service ne connaît pas Spring Events).
3.  **@EnableAsync :** Activation obligatoire dans le Main pour dire à Spring de créer un "Pool de Threads" (des ouvriers en arrière-plan) prêts à exécuter les méthodes `@Async`.

---

## Conclusion : La Règle d'Or de TaskSphere

Si vous devez retenir une seule règle en regardant le code de ce projet, c'est celle-ci :
**Le flux des dépendances ne doit jamais pointer vers l'intérieur du domaine. Les flèches vont toujours de l'extérieur (Adaptateurs/Contrôleurs) vers l'intérieur (Ports/Services/Domaine).**

***

**Fin du document.**