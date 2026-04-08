package com.tasksphere.core.repository;

import com.tasksphere.core.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/*
 * DESIGN PATTERN : Repository Pattern.
 * JpaRepository est MAGIQUE. Tu n'écris AUCUNE implémentation.
 * Tu dis juste : "Je gère des TaskEntity, et la clé primaire (ID) est un String".
 * Spring Data va générer TOUT LE CODE SQL tout seul (SELECT, INSERT, etc.) au démarrage.
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
    // Pas besoin de méthodes pour findAll() ou save(), JpaRepository les fournit déjà !
}