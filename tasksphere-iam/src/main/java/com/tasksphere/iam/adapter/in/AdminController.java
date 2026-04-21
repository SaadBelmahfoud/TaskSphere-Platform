package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.domain.UserAdminEvent;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.dto.UserAdminResponse;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 * EMPLACEMENT DANS LE MODULE IAM :
 * ────────────────────────────────────────
 * Ce contrôleur est dans tasksphere-iam (pas core) car il gère
 * des entités IAM (UserEntity). C'est cohérent avec l'architecture
 * modulaire : chaque module gère ses propres entités.
 *
 * ═══════════════════════════════════════════════════════════════════
 * CORRECTION — AUDIT LOG VIA ÉVÉNEMENTS (PAS D'IMPORT CROSS-MODULE)
 * ═══════════════════════════════════════════════════════════════════
 *
 * AVANT (erreur de build) :
 * ──────────────────────────
 * import com.tasksphere.core.domain.ActivityLog;          → ❌ IAM ne connaît pas Core
 * import com.tasksphere.core.service.ActivityLogService;  → ❌ IAM ne connaît pas Core
 * activityLogService.log(ActivityLog.Action.USER_ROLE_CHANGED, ...)
 *
 * → Erreur : "package com.tasksphere.core.domain does not exist"
 * → Cause : Dépendance cyclique Maven (IAM → Core → IAM)
 *
 * APRÈS (correction) :
 * ─────────────────────
 * import com.tasksphere.iam.domain.UserAdminEvent;       → ✅ Classe dans IAM
 * applicationEventPublisher.publishEvent(new UserAdminEvent(...))
 *
 * → L'AdminController publie un événement Spring
 * → Le module Core écoute cet événement via @EventListener
 * → Zéro dépendance cyclique
 *
 * PRINCIPE : APPLICATIONEVENTPUBLISHER (Spring)
 * ────────────────────────────────────────────
 * ApplicationEventPublisher est un composant Spring injecté via
 * constructor injection. Il permet de publier des événements
 * que n'importe quel @EventListener dans l'application peut capter.
 *
 * C'est le pattern Observer (GoF) implémenté par Spring :
 * - Publisher (ce contrôleur) → Event → Listener(s)
 * - Le publisher ne connaît PAS les listeners → loose coupling
 *
 * FLUX COMPLET DE L'AUDIT ADMIN :
 * ──────────────────────────────
 * 1. Admin change le rôle d'un utilisateur
 * 2. AdminController.updateUserRole() appelle publishEvent()
 * 3. Spring transmet l'événement à tous les @EventListener
 * 4. UserAdminEventListener (dans Core) reçoit l'événement
 * 5. Il crée un ActivityLog et le sauvegarde via ActivityLogPort
 * 6. L'audit est enregistré dans la table activity_logs
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

    /**
     * ApplicationEventPublisher : composant Spring pour publier des événements.
     *
     * RAPPEL (de Spring Events) :
     * Quand on publie un événement via ce publisher, tous les beans annotés
     * @EventListener (ou @TransactionalEventListener) qui écoutent ce type
     * d'événement seront notifiés automatiquement par Spring.
     *
     * POURQUOI CETTE APPROCHE ?
     * → L'AdminController (module IAM) ne peut PAS dépendre de Core.
     * → En publiant un événement, le listener DANS Core peut réagir
     *   sans que IAM ne connaisse Core.
     */
    private final ApplicationEventPublisher eventPublisher;

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
     *
     * AUDIT : Publie un UserAdminEvent(ROLE_CHANGED) capté par Core.
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

        // ═══════════════════════════════════════════════════════
        // AUDIT VIA ÉVÉNEMENT SPRING (pas d'import cross-module)
        // ═══════════════════════════════════════════════════════
        // AVANT : activityLogService.log(ActivityLog.Action.USER_ROLE_CHANGED, ...)
        //   → Erreur : ActivityLog et ActivityLogService sont dans Core,
        //     IAM ne peut pas les importer.
        //
        // APRÈS : publishEvent(new UserAdminEvent(...))
        //   → L'événement est dans IAM (UserAdminEvent.java)
        //   → Le listener est dans Core (UserAdminEventListener.java)
        //   → Spring fait le pont → zéro dépendance cyclique.
        String description = "Rôle de '" + user.getUsername() + "' changé de " + oldRole + " à " + newRole;
        eventPublisher.publishEvent(new UserAdminEvent(
                this,
                UserAdminEvent.AdminAction.ROLE_CHANGED,
                user.getUsername(),
                authentication.getName(),
                description
        ));

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
     *
     * AUDIT : Publie un UserAdminEvent(USER_TOGGLED) capté par Core.
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

        // ═══════════════════════════════════════════════════════
        // AUDIT VIA ÉVÉNEMENT SPRING (pas d'import cross-module)
        // ═══════════════════════════════════════════════════════
        String description = "Utilisateur '" + user.getUsername() + "' " + (newStatus ? "activé" : "désactivé");
        eventPublisher.publishEvent(new UserAdminEvent(
                this,
                UserAdminEvent.AdminAction.USER_TOGGLED,
                user.getUsername(),
                authentication.getName(),
                description
        ));

        log.info("ADMIN : Utilisateur {} {} par {}",
                user.getUsername(), newStatus ? "activé" : "désactivé", authentication.getName());

        return ResponseEntity.ok(Map.of("message",
                "Utilisateur " + (newStatus ? "activé" : "désactivé") + " avec succès"));
    }

    /**
     * Extrait le rôle depuis les authorities de Spring Security.
     * Les authorities sont formatées "ROLE_USER", "ROLE_ADMIN", etc.
     * On retire le préfixe "ROLE_" pour obtenir le rôle brut.
     */
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
    }
}