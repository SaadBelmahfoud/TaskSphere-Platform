package com.tasksphere.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;

/*
 * LE POINT D'ENTRÉ PRINCIPAL.
 *
 * Dans un Monolithe Modulaire, ce fichier est le "Chef d'Orchestre".
 * Même si on a séparé le code en modules, au moment du démarrage, tout est réuni ici.
 *
 * COMBIENÇON DE TECH LEAD : Ne JAMAIS laisser un sous-module gérer sa propre configuration JPA globale.
 * On déclare TOUT ici pour s'assurer qu'Hibernate voit toutes les tables et tous les repositories,
 * peu importe dans quel module ils sont physiquement situés.
 */
@EnableAsync
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.tasksphere.core",   // Scan le module Core
        "com.tasksphere.iam"      // Scan le module IAM
})
// Force Spring Data à scanner les interfaces Repository dans ces deux packages spécifiques
@EnableJpaRepositories(basePackages = {
        "com.tasksphere.core.adapter.out.persistence", // Pour TaskRepository
        "com.tasksphere.iam.port.out"               // Pour UserRepository
})
// Force Hibernate à scanner les classes @Entity dans ces deux packages spécifiques
@EntityScan(basePackages = {
        "com.tasksphere.core.domain", // Pour TaskEntity
        "com.tasksphere.iam.domain"  // Pour UserEntity
})
public class TasksphereCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TasksphereCoreApplication.class, args);
    }
}