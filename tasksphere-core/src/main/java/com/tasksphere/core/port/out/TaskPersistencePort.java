package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import java.util.List;

/*
 * ARCHITECTURE HEXAGONALE : LE PORT SORTANT (OUTPUT PORT)
 *
 * POURQUOI CETTE INTERFACE ?
 * C'est le contrat que notre logique métier exige pour sauvegarder des données.
 * Remarque cruciale : IL N'Y A AUCUNE IMPORT DE JPA, SQL, OU ENTITY ICI.
 *
 * Le Domaine dicte ses règles au monde extérieur, et non l'inverse.
 * Si demain on change de BDD, cette interface NE BOUGERA PAS. Seul l'adaptateur changera.
 */
public interface TaskPersistencePort {

    /*
     * On utilise l'objet Domaine (Task) et non l'entité (TaskEntity).
     * C'est la garantie que l'extérieur doit nous ramener du pur métier.
     */
    Task save(Task task);
    List<Task> findAll();
}