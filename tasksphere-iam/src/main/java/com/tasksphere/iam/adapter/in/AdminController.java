package com.tasksphere.iam.adapter.in;

import com.tasksphere.core.domain.ActivityLog;
import com.tasksphere.core.service.ActivityLogService;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.dto.UserAdminResponse;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════════
 * ADAPTATEUR D'ENTRÉE : AdminController (Gestion des utilisateurs)
 * ═══════════════════════════════════════════════════════════════════
 *
 * NOUVEAU — ADMINISTRATION DES UTILISATEURS :
 * ───────────────────────────────────────────
 * Ce contrôleur permet à un ADMIN de :
 * 1. Lister tous les utilisateurs
 * 2. Changer le rôle d'un utilisateur
 * 3. Activer/désactiver un utilisateur
 *
 * RBAC : Toutes les méthodes vérifient que l'appelant est ADMIN.
 *
 *EMPLACEMENT DANS LE MODULE IAM :
 * ────────────────────────────────────────
 * Ce contrôleur est dans tasksphere-iam (pas core) car il gère
 * des entités IAM (UserEntity). C'est cohérent avec l'architecture
 * modulaire : chaque module gère ses propres entités.
 *
 * Cependant, il dépend d'ActivityLogService (module core) pour
 * tracer les actions d'administration. C'est acceptable car
 * ActivityLogService est un service transversal.
 *
 * ENDPOINTS :
 * ──────────
 * GET   /api/v1/iam/admin/users              → Lister tous les utilisateurs
 * PATCH /api/v1/iam/admin/users/{id}/role    → Changer le rôle
 * PATCH /api/v1/iam/admin/users/{id}/toggle  → Activer/désactiver
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/iam/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    /**
     * GET /api/v1/iam/admin/users
     *
     * Liste tous les utilisateurs du système.
     * ACCESSIBLE UNIQUEMENT AUX ADMINS (vérifié dans le code).
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        String role = extractRole(authentication);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Accès réservé aux administrateurs"));
        }

        log.info("ADMIN : Liste des utilisateurs demandée par {}", authentication.getName());
        List<UserAdminResponse> users = userRepository.findAll().stream()
                .map(UserAdminResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(users);
    }

    /**
     * PATCH /api/v1/iam/admin/users/{userId}/role
     *
     * Change le rôle d'un utilisateur.
     * CORPS : { "role": "MANAGER" }
     * Rôles valides : USER, MANAGER, ADMIN
     */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String role = extractRole(authentication);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Accès réservé aux administrateurs"));
        }

        String newRole = request.get("role");
        if (newRole == null || !List.of("USER", "MANAGER", "ADMIN").contains(newRole)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Rôle invalide. Valeurs : USER, MANAGER, ADMIN"));
        }

        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Utilisateur non trouvé"));
        }

        UserEntity user = userOpt.get();
        String oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);

        // Tracer dans l'audit log
        activityLogService.log(
                ActivityLog.Action.USER_ROLE_CHANGED,
                "Rôle de '" + user.getUsername() + "' changé de " + oldRole + " à " + newRole,
                authentication.getName(), null, null
        );

        log.info("ADMIN : Rôle de {} changé de {} à {} par {}",
                user.getUsername(), oldRole, newRole, authentication.getName());

        return ResponseEntity.ok(Map.of("message", "Rôle mis à jour avec succès"));
    }

    /**
     * PATCH /api/v1/iam/admin/users/{userId}/toggle
     *
     * Active ou désactive un utilisateur.
     * Si enabled=true → passe à false (désactivation)
     * Si enabled=false → passe à true (activation)
     */
    @PatchMapping("/users/{userId}/toggle")
    public ResponseEntity<?> toggleUserStatus(
            @PathVariable String userId,
            Authentication authentication) {

        String role = extractRole(authentication);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Accès réservé aux administrateurs"));
        }

        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Utilisateur non trouvé"));
        }

        UserEntity user = userOpt.get();
        boolean newStatus = !user.getEnabled();
        user.setEnabled(newStatus);
        userRepository.save(user);

        // Tracer dans l'audit log
        activityLogService.log(
                ActivityLog.Action.USER_TOGGLED,
                "Utilisateur '" + user.getUsername() + "' " + (newStatus ? "activé" : "désactivé"),
                authentication.getName(), null, null
        );

        log.info("ADMIN : Utilisateur {} {} par {}",
                user.getUsername(), newStatus ? "activé" : "désactivé", authentication.getName());

        return ResponseEntity.ok(Map.of("message",
                "Utilisateur " + (newStatus ? "activé" : "désactivé") + " avec succès"));
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }
}