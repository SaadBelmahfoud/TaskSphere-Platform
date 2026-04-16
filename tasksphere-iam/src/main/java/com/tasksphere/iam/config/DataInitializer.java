package com.tasksphere.iam.config;

import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component // OBLIGATOIRE pour être scanné par le @ComponentScan
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("INIT DB IAM : La base est vide. Création de l'utilisateur par défaut...");
            String rawPassword = "password123";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            UserEntity admin = new UserEntity(null, "saadoune", encodedPassword, "USER");
            userRepository.save(admin);
            log.info("INIT DB IAM : Utilisateur 'saadoune' créé avec succès (Mot de passe hashé par BCrypt).");
        } else {
            log.info("INIT DB IAM : Base de données déjà peuplée, aucune action requise.");
        }
    }
}