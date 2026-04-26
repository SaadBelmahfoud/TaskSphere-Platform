package com.tasksphere.iam.dto;

import com.tasksphere.iam.domain.UserEntity;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 * DTO DE SORTIE : Réponse utilisateur pour l'admin
 * ═══════════════════════════════════════════════════════════════════
 *
 * SÉCURITÉ : On n'expose JAMAIS le mot de passe (même hashé).
 * Seules les informations nécessaires à l'administration sont incluses.
 */
public record UserAdminResponse(
        String id,
        String username,
        String email,
        String role,
        String firstName,
        String lastName,
        boolean enabled
) {
    public static UserAdminResponse fromEntity(UserEntity user) {
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getFirstName(),
                user.getLastName(),
                user.getEnabled()
        );
    }
}