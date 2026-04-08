package com.tasksphere.core;

import com.tasksphere.core.service.TaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/*
 * @SpringBootApplication : Annotation "Superpowers" qui combine :
 * - @Configuration : Dit que cette classe est une source de configuration.
 * - @EnableAutoConfiguration : Configure automatiquement Spring en fonction des dépendances (Lombok, etc.).
 * - @ComponentScan : Dit à Spring de fouiller le package "com.tasksphere.core" pour trouver les @Service.
 */
@Slf4j
@SpringBootApplication
public class TasksphereCoreApplication {

    public static void main(String[] args) {
        // Ici, on lance le Conteneur Spring IoC. À partir de ce moment, Spring "prend le contrôle".
        SpringApplication.run(TasksphereCoreApplication.class, args);
    }

    /*
     * DESIGN PATTERN : Command Pattern (exécuté au démarrage)
     * Ce Bean s'exécute automatiquement une fois que le Conteneur IoC a démarré.
     * Regarde bien la méthode : elle demande un "TaskManager".
     * On ne fait PAS "new TaskManager()". On le demande à Spring.
     */
    @Bean
    CommandLineRunner start(TaskManager taskManager) {
        return args -> {
            log.info("=== DÉMARRAGE DE TASKSPHERE V1 ===");

            // Utilisation du service
            taskManager.createTask("Initialiser l'architecture", "Mise en place Maven");
            taskManager.createTask("Définir le Domain Model", "Création des Records");

            log.info("--- AFFICHAGE DES TÂCHES ---");
            taskManager.getAllTasks().forEach(task -> log.info("Tâche : {}", task));

            log.info("=== FIN DU PROGRAMME ===");
        };
    }
}