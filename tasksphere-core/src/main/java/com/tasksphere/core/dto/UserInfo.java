package com.tasksphere.core.dto;

/*
 * DTO (Data Transfer Object) de transition.
 * Il sert de structure de données pour transporter les infos de l'IAM vers le Core.
 *
 * Attention : En Java Record, le nom des variables DÉFINIT le nom des méthodes.
 * String username -> génère la méthode username()
 * String role     -> génère la méthode role()
 */
public record UserInfo(String name, String userRole) {
}