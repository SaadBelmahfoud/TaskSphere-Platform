package com.tasksphere.iam.config;

import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/*
 * ====================================================================
 * INITIALISATEUR DE DONNÉES (Seed Data)
 * ====================================================================
 *
 * PRINCIPE :
 * Ce composant s'exécute au démarrage de l'application.
 * Si la BDD est vide, il crée des utilisateurs de test.
 * Cela permet d'avoir des données pour tester immédiatement.
 *
 * PRINCIPE CommandLineRunner :
 * Interface Spring avec une seule méthode run().
 * Exécutée APRÈS le chargement complet du contexte Spring.
 * @Order(1) garantit l'ordre d'exécution si plusieurs CommandLineRunner existent.
 *
 * SPRINT 1 : On crée 3 utilisateurs pour tester les rôles.
 *
 * ====================================================================
 * CORRECTION B10 — VARIABLES D'ENVIRONNEMENT + PAS DE LOGGING DES CREDENTIALS
 * ====================================================================
 *
 * PROBLÈME AVANT :
 * 1. Mots de passe codés en dur dans le code source ("password123")
 *    → Si le code est leaké (GitHub public), les mots de passe sont compromis.
 *    → Impossible de changer les mots de passe sans recompiler.
 *
 * 2. Logging des credentials en clair dans les logs :
 *    log.info("  - saadoune / password123 (USER)")
 *    → Les logs peuvent être consultés par des personnes non autorisées.
 *    → En production, les logs sont souvent collectés par des outils tiers (ELK, Datadog).
 *    → C'est une VIOLATION DE SÉCURITÉ (OWASP Top 10 - Sensitive Data Exposure).
 *
 * SOLUTION APRÈS :
 * 1. Mot de passe configurable via variable d'environnement INIT_USER_PASSWORD
 *    → En prod : export INIT_USER_PASSWORD=SuperSecret123
 *    → En dev : la valeur par défaut est utilisée
 *
 * 2. Plus AUCUN logging des credentials.
 *    → On log les usernames et les rôles, JAMAIS les mots de passe.
 *    → Si on a besoin de vérifier le mot de passe, on le fait manuellement en local.
 *
 * PRINCIPE @Value("${init.user.password:password123}") :
 * - Lit la propriété "init.user.password" depuis application.yaml ou variable d'env
 * - Si la variable d'environnement INIT_USER_PASSWORD existe → utilise cette valeur
 * - Sinon → utilise "password123" comme valeur par défaut
 * - NOTE : Spring convertit les variables d'env en propriétés en remplaçant les points par des underscores
 *   et en majuscules : init.user.password → INIT_USER_PASSWORD
 *
 * ====================================================================
 * CORRECTION SPRINT 5 — Constructeur UserEntity à 11 arguments
 * ====================================================================
 *
 * PROBLÈME :
 *   UserEntity avait 9 champs avant Sprint 5. Sprint 5 a ajouté createdAt
 *   et lastLogin pour correspondre aux colonnes Flyway V1.
 *   L'annotation @AllArgsConstructor génère maintenant un constructeur
 *   avec 11 paramètres, mais DataInitializer n'en passait que 9 :
 *
 *   AVANT (9 args) :
 *     new UserEntity(null, username, email, encodedPassword, role,
 *                     firstName, lastName, null, true)
 *     → ERREUR COMPILATION : "no suitable constructor found"
 *
 *   APRÈS (11 args) :
 *     new UserEntity(null, username, email, encodedPassword, role,
 *                     firstName, lastName, null, true, null, null)
 *     → id=null (auto-généré par @GeneratedValue)
 *     → avatarUrl=null (optionnel)
 *     → enabled=true (compte actif)
 *     → createdAt=null (@PrePersist valorise automatiquement avant l'INSERT)
 *     → lastLogin=null (jamais connecté)
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Mot de passe des utilisateurs de test.
     * Configurable via variable d'environnement INIT_USER_PASSWORD.
     * Valeur par défaut : "password123" (pour le dev uniquement).
     *
     * PRINCIPE DE SÉCURITÉ :
     * Ne JAMAIS coder un mot de passe en dur dans le code source.
     * Les variables d'environnement sont le standard de l'industrie pour les secrets.
     */
    @Value("${init.user.password:password123}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("INIT DB : Base déjà peuplée ({} utilisateurs), aucune action requise.", userRepository.count());
            return;
        }

        log.info("INIT DB : Création des utilisateurs de test...");

        // Utilisateur standard (USER)
        createUser("saadoune", "saadoune@tasksphere.com", "USER",
                "Saad", "Belmahfoud");

        // Utilisateur manager (MANAGER)
        createUser("manager", "manager@tasksphere.com", "MANAGER",
                "Manager", "TaskSphere");

        // Administrateur (ADMIN)
        createUser("admin", "admin@tasksphere.com", "ADMIN",
                "Admin", "TaskSphere");

        // CORRECTION B10 : On log les usernames et les rôles, JAMAIS les mots de passe !
        log.info("INIT DB : 3 utilisateurs créés avec succès.");
        log.info("  - saadoune ({})", "USER");
        log.info("  - manager ({})", "MANAGER");
        log.info("  - admin ({})", "ADMIN");
    }

    /**
     * Crée un utilisateur avec un mot de passe hashé par BCrypt.
     *
     * PRINCIPE BCrypt :
     * - passwordEncoder.encode(rawPassword) génère un hash unique (même pour le même password)
     * - Le sel (salt) est inclus dans le hash → pas besoin de stocker le sel séparément
     * - La vérification se fait avec passwordEncoder.matches(rawPassword, hashedPassword)
     *
     * @param username    Nom d'utilisateur unique
     * @param email       Email de l'utilisateur
     * @param role        Rôle (USER, MANAGER, ADMIN)
     * @param firstName   Prénom
     * @param lastName    Nom de famille
     */
    private void createUser(String username, String email, String role,
                            String firstName, String lastName) {
        // Le mot de passe vient de la variable d'environnement (ou valeur par défaut)
        String encodedPassword = passwordEncoder.encode(defaultPassword);

        // CORRECTION SPRINT 5 : Constructeur à 11 arguments
        // ────────────────────────────────────────────────────
        // L'annotation @AllArgsConstructor de UserEntity génère un constructeur
        // avec TOUS les champs dans l'ordre de déclaration :
        //   id, username, email, password, role, firstName, lastName,
        //   avatarUrl, enabled, createdAt, lastLogin
        //
        // Arguments :
        //   null             → id : auto-généré par @GeneratedValue(strategy = UUID)
        //   username         → username
        //   email            → email
        //   encodedPassword  → password (hash BCrypt)
        //   role             → role (USER, MANAGER, ADMIN)
        //   firstName        → firstName
        //   lastName         → lastName
        //   null             → avatarUrl (optionnel, pas d'avatar par défaut)
        //   true             → enabled (compte activé immédiatement)
        //   null             → createdAt (valorisé par @PrePersist avant l'INSERT)
        //   null             → lastLogin (jamais connecté, nullable)
        UserEntity user = new UserEntity(
                null, username, email, encodedPassword, role,
                firstName, lastName, null, true, null, null
        );
        userRepository.save(user);
    }
}