package com.tasksphere.core.dto;

import com.tasksphere.core.domain.Task; // Seul le DTO a le droit de voir le Domaine

/*
 * CE FICHIER REPRÉSENTE CE QUE LE CLIENT VA RECEVOIR.
 * Pourquoi ne pas renvoyer directement l'objet Task (Domaine) ?
 * Sécurité et Évolution : Si demain on ajoute un champ "motDePasseAdmin" dans Task,
 * il ne sera PAS dans ce DTO, donc il ne sera jamais envoyé sur internet.
 */
public record TaskResponse(String id, String title, String description) {

    /*
     * DESIGN PATTERN : Mapper (Conversion).
     * C'est la seule fonction de ce fichier : prendre un objet Domaine et le traduire en DTO.
     * "static" permet de l'appeler sans instancier la classe : TaskResponse.fromDomain(task)
     */
    public static TaskResponse fromDomain(Task task) {
        return new TaskResponse(task.id(), task.title(), task.description());
    }
}