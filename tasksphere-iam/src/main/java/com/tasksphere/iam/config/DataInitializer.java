package com.tasksphere.iam.config;

import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * SPRINT 1 : On crée 3 utilisateurs pour tester les rôles.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("INIT DB : Base déjà peuplée ({} utilisateurs), aucune action requise.", userRepository.count());
            return;
        }

        log.info("INIT DB : Création des utilisateurs de test...");

        // Utilisateur standard (USER)
        createUser("saadoune", "saadoune@tasksphere.com", "password123", "USER",
                "Saad", "Belmahfoud");

        // Utilisateur manager (MANAGER)
        createUser("manager", "manager@tasksphere.com", "password123", "MANAGER",
                "Manager", "TaskSphere");

        // Administrateur (ADMIN)
        createUser("admin", "admin@tasksphere.com", "password123", "ADMIN",
                "Admin", "TaskSphere");

        log.info("INIT DB : 3 utilisateurs créés avec succès.");
        log.info("  - saadoune / password123 (USER)");
        log.info("  - manager  / password123 (MANAGER)");
        log.info("  - admin    / password123 (ADMIN)");
    }

    /**
     * Crée un utilisateur avec un mot de passe hashé par BCrypt.
     */
    private void createUser(String username, String email, String rawPassword,
                            String role, String firstName, String lastName) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserEntity user = new UserEntity(
                null, username, email, encodedPassword, role,
                firstName, lastName, null, true
        );
        userRepository.save(user);
    }
}