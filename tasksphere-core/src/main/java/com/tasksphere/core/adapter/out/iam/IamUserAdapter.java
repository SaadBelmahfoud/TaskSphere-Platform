package com.tasksphere.core.adapter.out.iam;

import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.UserInformationPort;
import com.tasksphere.iam.port.in.IamQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/*
 * L'ADAPTATEUR DE PONT.
 * Il implémente le Port du Core, mais pour le faire, il utilise directement un Bean de IAM.
 * AUCUN APPEL HTTP. Juste un appel de méthode Java : iamQueryService.getUserRoleByEmail()
 *
 * ═══════════════════════════════════════════════════════════
 * CORRECTION — Ajout de resolveAssigneeToEmail()
 * ═══════════════════════════════════════════════════════════
 *
 * Cette méthode résout un UUID en email avant le stockage dans
 * assigneeId. C'est une défense en profondeur : même si le
 * frontend envoie accidentellement un UUID, le backend le
 * convertit en email avant de le stocker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamUserAdapter implements UserInformationPort {

    // Spring injecte automatiquement le Bean de IAM car il est dans le classpath !
    private final IamQueryService iamQueryService;

    @Override
    public UserInfo getUserInfo(String username) {
        log.debug("ADAPTATEUR IAM : Appel direct en mémoire vers le module IAM pour {}", username);

        // ← CORRIGÉ : avant on appelait getUserRole(username) qui cherche par UUID
        // Or username = email (venant du JWT subject), donc findById(email) ne trouvait rien.
        // Maintenant on appelle getUserRoleByEmail(username) qui cherche par email.
        String role = iamQueryService.getUserRoleByEmail(username).orElse("ROLE_UNKNOWN");
        return new UserInfo(username, role);
    }

    /**
     * ═══════════════════════════════════════════════════════════
     * CORRECTION — Résoudre un UUID en email pour l'assignation
     * ═══════════════════════════════════════════════════════════
     *
     * LOGIQUE :
     * 1. Si l'input est null ou vide → retourner null (pas d'assignation)
     * 2. Si l'input contient "@" → c'est déjà un email → retourner tel quel
     * 3. Sinon, supposer que c'est un UUID → chercher l'email via IAM
     * 4. Si l'email est trouvé → le retourner
     * 5. Si l'UUID n'est pas trouvé → logger un WARN et retourner l'input original
     *
     * POURQUOI UN FALLBACK ?
     * Si la résolution échoue, on ne veut PAS faire échouer l'opération
     * entière. On stocke la valeur originale et on loggue un avertissement.
     * Cela permet au système de continuer à fonctionner même si les données
     * IAM sont temporairement incohérentes.
     *
     * EXEMPLE DE FLUX :
     * Frontend envoie: { "assigneeId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" }
     * → IAM résout: "saadoune@tasksphere.com"
     * → Backend stocke: assignee_id = "saadoune@tasksphere.com"
     * → Query: WHERE assigneeId = 'saadoune@tasksphere.com' → ✅ MATCH
     */
    @Override
    public String resolveAssigneeToEmail(String assigneeId) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return null;
        }

        // Si c'est déjà un email (contient @), le retourner tel quel
        if (assigneeId.contains("@")) {
            log.debug("ADAPTATEUR IAM : assigneeId '{}' est déjà un email, pas de résolution nécessaire", assigneeId);
            return assigneeId;
        }

        // Sinon, supposer que c'est un UUID et chercher l'email correspondant
        log.debug("ADAPTATEUR IAM : Résolution de l'UUID '{}' en email...", assigneeId);
        var emailOpt = iamQueryService.findEmailById(assigneeId);

        if (emailOpt.isPresent()) {
            String resolvedEmail = emailOpt.get();
            log.info("ADAPTATEUR IAM : UUID '{}' résolu en email '{}'", assigneeId, resolvedEmail);
            return resolvedEmail;
        }

        // Fallback : UUID non trouvé → stocker la valeur originale et avertir
        log.warn("ADAPTATEUR IAM : Impossible de résoudre l'UUID '{}' en email. " +
                "La valeur sera stockée telle quelle. Les requêtes de recherche par assigneeId " +
                "pourront ne pas fonctionner correctement.", assigneeId);
        return assigneeId;
    }
}