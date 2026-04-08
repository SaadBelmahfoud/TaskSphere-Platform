package com.tasksphere.core.domain;

import java.util.UUID;

/*
 * Un "Record" est une classe strictement immuable (pas de setters).
 * Pourquoi l'immuabilité ? Dans un système industriel, si deux utilisateurs
 * modifient la même tâche en même temps, l'immuabilité empêche les bugs d'écrasement.
 * On ne modifie pas une tâche, on en crée une nouvelle avec les nouvelles infos.
 */
public record Task(String id, String title, String description) {

    /*
     * DESIGN PATTERN : Factory Method (Méthode de Fabrique)
     * Pourquoi ne pas mettre le UUID dans le constructeur ?
     * Parce qu'on veut cacher la complexité de la génération d'ID.
     * Celui qui crée la tâche n'a pas besoin de savoir comment l'ID est généré.
     */
    public static Task create(String title, String description) {
        return new Task(UUID.randomUUID().toString(), title, description);
    }
}