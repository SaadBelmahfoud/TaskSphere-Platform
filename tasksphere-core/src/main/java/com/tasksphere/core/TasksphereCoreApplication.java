package com.tasksphere.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;

/*
 * ====================================================================
 * POINT D'ENTRÉE PRINCIPAL (Main class)
 * ====================================================================
 *
 * PRINCIPE MONOLITHE MODULAIRE :
 * Toutes les configurations de scan sont centralisées ici.
 * C'est nécessaire car on a un seul ApplicationContext pour tous les modules.
 *
 * SPRINT 1 : Ajout du scan pour RefreshTokenEntity.
 */
@EnableAsync
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.tasksphere.core",
        "com.tasksphere.iam"
})
@EnableJpaRepositories(basePackages = {
        "com.tasksphere.core.adapter.out.persistence",
        "com.tasksphere.iam.port.out"
})
@EntityScan(basePackages = {
        "com.tasksphere.core.adapter.out.persistence",
        "com.tasksphere.iam.domain"
})
public class TasksphereCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TasksphereCoreApplication.class, args);
    }
}