package com.tasksphere.core.port.out;

import com.tasksphere.core.dto.UserInfo;

/**
 * ═══════════════════════════════════════════════════════════
 * CORRECTION — Ajout de resolveAssigneeToEmail()
 * ═══════════════════════════════════════════════════════════
 *
 * PROBLÈME :
 * Le frontend envoie parfois un UUID (user.id) comme assigneeId
 * au lieu d'un email (user.email). Le backend stocke ce UUID
 * directement dans assignee_id, mais les requêtes JPQL comparent
 * assigneeId avec l'email du JWT.
 * UUID ≠ email → la query ne matche JAMAIS.
 *
 * SOLUTION :
 * Avant de stocker un assigneeId, on le résout en email si
 * c'est un UUID. Si c'est déjà un email, on le garde tel quel.
 * Si la résolution échoue (UUID non trouvé), on stocke la valeur
 * originale pour ne pas casser le flux existant.
 */
public interface UserInformationPort {
    UserInfo getUserInfo(String username);

    /**
     * Résout un assigneeId en email si c'est un UUID.
     *
     * LOGIQUE :
     * 1. Si l'input contient "@" → c'est déjà un email → retourner tel quel
     * 2. Si l'input ressemble à un UUID → chercher l'email correspondant
     * 3. Si l'UUID n'est pas trouvé → retourner l'input original (fallback)
     *
     * Cette méthode est utilisée par TaskManager.assignTask() et
     * TaskManager.createTask() avant de stocker l'assigneeId.
     *
     * @param assigneeId l'ID ou email de l'assignataire
     * @return l'email résolu, ou l'input original si la résolution échoue
     */
    String resolveAssigneeToEmail(String assigneeId);
}