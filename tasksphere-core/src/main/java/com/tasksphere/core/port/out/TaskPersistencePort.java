package com.tasksphere.core.port.out;

import com.tasksphere.core.domain.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/*
 * ====================================================================
 * PORT SORTANT : Persistance des tâches (Contrat du domaine)
 * ====================================================================
 *
 * PRINCIPE DDD (Domain-Driven Design) :
 * Le domaine définit SON contrat. L'infrastructure (JPA, SQL) doit s'adapter.
 * C'est l'inversion de dépendance : le domaine dicte ses besoins, l'adapter obéit.
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * Un "Port" est une interface Java que le domaine expose.
 * - Port ENTRANT (in) : ce que le domaine offre (ex: Use Case interface)
 * - Port SORTANT (out) : ce dont le domaine a besoin (ex: persistance, messaging)
 *
 * ICI : C'est un port SORTANT car le domaine a besoin de sauvegarder/lire des tâches.
 * L'adaptateur (TaskPersistenceAdapter) implémentera cette interface.
 *
 * AVANTAGES DE CETTE APPROCHE :
 * 1. TESTABILITÉ : On peut mocker cette interface dans les tests unitaires
 * 2. FLEXIBILITÉ : On peut changer d'implémentation (JPA → MongoDB → Redis) sans toucher le domaine
 * 3. INDÉPENDANCE : Le domaine ne dépend d'aucune technologie spécifique
 *
 * SPRINT 1 : Ajout de findById, update, et filtrage par utilisateur.
 * SPRINT 2 : Ajout de searchTasks avec critères dynamiques (Parameter Object Pattern).
 * SPRINT 3 (Section 6) : Ajout des méthodes de comptage pour le Dashboard.
 */
public interface TaskPersistencePort {

    /** Sauvegarder une tâche (création ou mise à jour) */
    Task save(Task task);

    /**
     * Récupérer toutes les tâches actives d'un utilisateur avec pagination.
     * "Actives" = deletedAt IS NULL (soft delete filter).
     */
    Page<Task> findByUserId(String userId, Pageable pageable);

    /** Récupérer une tâche active par son ID (deletedAt IS NULL) */
    Optional<Task> findById(String id);

    /**
     * Récupérer une tâche active par ID et par utilisateur (pour vérifier l'ownership).
     * Combine les deux filtres : id = ? AND userId = ? AND deletedAt IS NULL
     */
    Optional<Task> findByIdAndUserId(String id, String userId);

    /**
     * ═══════════════════════════════════════════════════════════
     * CORRECTION BUG 1 — Recherche par ID + (propriétaire OU assignataire)
     * ═══════════════════════════════════════════════════════════
     *
     * PROBLÈME AVANT :
     * findByIdAndUserId() ne cherche QUE le propriétaire (userId).
     * Un utilisateur qui est assignataire (mais pas créateur) reçoit un 404
     * quand il tente de voir le détail d'une tâche qui lui est assignée.
     *
     * SOLUTION :
     * Chercher la tâche si l'utilisateur est créateur (userId) OU
     * assignataire (assigneeId). Cela permet à un USER de voir les
     * tâches qu'il a créées ET celles qu'on lui a assignées.
     *
     * PRINCIPE RBAC ÉLARGI :
     * ┌──────────────────────────────────────────────────────────┐
     * │  AVANT : USER ne voit que ses tâches CRÉÉES              │
     * │  APRÈS : USER voit ses tâches CRÉÉES + ASSIGNÉES         │
     * └──────────────────────────────────────────────────────────┘
     *
     * @param id       L'ID de la tâche
     * @param username L'email de l'utilisateur (créateur ou assignataire)
     * @return La tâche si trouvée et l'utilisateur est impliqué
     */
    Optional<Task> findByIdAndUserIsOwnerOrAssignee(String id, String username);

    /**
     * ═══════════════════════════════════════════════════════════
     * CORRECTION BUG 1 — Liste des tâches d'un utilisateur (propriétaire OU assignataire)
     * ═══════════════════════════════════════════════════════════
     *
     * PROBLÈME AVANT :
     * findByUserId() ne retourne que les tâches dont l'utilisateur
     * est le propriétaire (userId). Les tâches assignées à cet
     * utilisateur (assigneeId) n'apparaissent PAS dans sa liste.
     *
     * SOLUTION :
     * Retourner les tâches où l'utilisateur est créateur (userId)
     * OU assignataire (assigneeId), avec pagination.
     *
     * UTILISÉE PAR : TaskManager.getMyTasks()
     *
     * @param username L'email de l'utilisateur
     * @param pageable La pagination
     * @return Une page de tâches où l'utilisateur est impliqué
     */
    Page<Task> findByUserIsOwnerOrAssignee(String username, Pageable pageable);

    /** Supprimer logiquement une tâche (soft delete : set deletedAt = now) */
    void softDelete(String id);

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE (Sprint 2)
    // ═══════════════════════════════════════════════════════

    /**
     * Recherche dynamique avec filtres optionnels.
     *
     * PRINCIPE PARAMETER OBJECT :
     * Au lieu de passer 9 paramètres (keyword, userId, assigneeId, status,
     * priority, dueDateFrom, dueDateTo, createdFrom, createdTo), on encapsule
     * tous les critères dans un record TaskSearchCriteria.
     *
     * AVANTAGES DU PARAMETER OBJECT :
     * 1. LISIBILITÉ : La signature est propre (1 paramètre au lieu de 9)
     * 2. EXTENSIBILITÉ : Ajouter un filtre = ajouter un champ au record
     * 3. IMMUTABILITÉ : Un record est immutable → pas d'effets de bord
     * 4. TYPAGE FORT : Chaque critère a son type (String, enum, LocalDate, etc.)
     *
     * @param criteria Les critères de recherche (tous optionnels)
     * @param pageable La pagination (page, size, sort)
     * @return Une page de tâches correspondant aux critères
     */
    Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable);

    /**
     * ═══════════════════════════════════════════════════════════
     * CORRECTION BUG 1 — Recherche dynamique pour USER (propriétaire OU assignataire)
     * ═══════════════════════════════════════════════════════════
     *
     * PROBLÈME AVANT :
     * searchTasks() utilise userId ET assigneeId comme filtres AND.
     * Pour un USER, le TaskManager faisait DEUX requêtes séparées
     * (owned + assigned) puis fusionnait les résultats. Cette approche
     * est CASSÉE pour la pagination :
     * - totalElements est incorrect (Math.max au lieu du vrai count)
     * - La fusion de 2 pages ne produit PAS une pagination valide
     *
     * SOLUTION :
     * UNE SEULE requête qui utilise (userId = :username OR assigneeId = :username)
     * dans la clause WHERE. La BDD gère le OR nativement, la pagination
     * est correcte, et il n'y a PAS de doublons.
     *
     * PRINCIPE — OR logique dans une seule requête vs fusion de 2 requêtes :
     * ┌──────────────────────────────────────────────────────────────────┐
     * │  AVANT (2 requêtes) :                                            │
     * │  1. SELECT ... WHERE userId = :user → Page A (5 items, total=5) │
     * │  2. SELECT ... WHERE assigneeId = :user → Page B (3 items, total=3) │
     * │  3. Fusion : Stream.concat(A, B).distinct() = 6 items           │
     * │  4. totalElements = Math.max(5, 3) = 5 → FAUX ! (devrait être 6)│
     * │  5. Pagination cassée : page 2 peut être vide ou incomplète      │
     * │                                                                  │
     * │  APRÈS (1 requête) :                                             │
     * │  1. SELECT ... WHERE (userId = :user OR assigneeId = :user)      │
     * │     AND ... autres filtres ...                                   │
     * │  2. Page complète avec totalElements EXACT                       │
     * │  3. Pagination correcte : page 0, 1, 2... fonctionnent           │
     * └──────────────────────────────────────────────────────────────────┘
     *
     * UTILISÉE PAR : TaskManager.searchTasks() pour USER uniquement.
     * Pour ADMIN/MANAGER, on utilise searchTasks() standard (sans filtre user).
     *
     * @param username L'email de l'utilisateur (toujours non-null pour USER)
     * @param criteria Les critères de recherche (keyword, status, priority, etc.)
     * @param pageable La pagination
     * @return Une page de tâches où l'utilisateur est impliqué
     */
    Page<Task> searchTasksForUser(String username, TaskSearchCriteria criteria, Pageable pageable);

    // ═══════════════════════════════════════════════════════
    // PARAMETER OBJECT : TaskSearchCriteria
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * PARAMETER OBJECT : TaskSearchCriteria
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE : Parameter Object Pattern
     * Quand une méthode a trop de paramètres (ici 9), on les regroupe
     * dans un record dédié. C'est un refactoring classique.
     *
     * POURQUOI UN INNER RECORD (dans l'interface) ?
     * - Cohérence : ce record n'a de sens QUE pour ce port
     * - Encapsulation : il n'est visible que via TaskPersistencePort.TaskSearchCriteria
     * - Simplicité : pas besoin d'un fichier séparé pour un record de 10 lignes
     *
     * FILTRES DISPONIBLES (9 filtres + pagination) :
     * ────────────────────────────────────────────
     * 1. keyword     → Recherche textuelle (titre OU description)
     * 2. userId      → Filtrer par créateur (pour ADMIN/MANAGER)
     * 3. assigneeId  → Filtrer par assignataire
     * 4. status      → Filtrer par statut (TODO/DOING/DONE)
     * 5. priority    → Filtrer par priorité (LOW/MEDIUM/HIGH/CRITICAL)
     * 6. dueDateFrom → Date d'échéance minimum (>=)
     * 7. dueDateTo   → Date d'échéance maximum (<=)
     * 8. createdFrom → Date de création minimum (>=)
     * 9. createdTo   → Date de création maximum (<=)
     *
     * UTILISATION DANS LE CONTROLLER (TaskController.getTasks()) :
     * new TaskPersistencePort.TaskSearchCriteria(keyword, null, assigneeId, ...)
     * → Le userId est null car il est injecté par le service selon le rôle
     *    (RBAC : USER ne voit que ses tâches, ADMIN/MANAGER voient tout)
     *
     * UTILISATION DANS LE SERVICE (TaskManager.searchTasks()) :
     * → ADMIN/MANAGER : les critères passés tels quels (recherche globale)
     * → USER : deux recherches sont faites (owned + assigned) puis fusionnées
     */
    record TaskSearchCriteria(
            String keyword,        // Recherche textuelle (titre ou description)
            String userId,         // Filtrer par créateur (injecté par RBAC)
            String assigneeId,     // Filtrer par assignataire
            Task.TaskStatus status,        // Filtrer par statut
            Task.TaskPriority priority,    // Filtrer par priorité
            LocalDate dueDateFrom,         // Date d'échéance minimum
            LocalDate dueDateTo,           // Date d'échéance maximum
            LocalDateTime createdFrom,     // Date de création minimum
            LocalDateTime createdTo        // Date de création maximum
    ) {}

    // ═══════════════════════════════════════════════════════
    // STATISTIQUES POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * MÉTHODES DE COMPTAGE — Section 6 : Dashboard & Collaboration
     * ═══════════════════════════════════════════════════════════
     *
     * POURQUOI DES MÉTHODES DE COMPTAGE SPÉCIFIQUES ?
     * ────────────────────────────────────────────
     * Le DashboardService doit afficher des KPI :
     * - Nombre total de tâches
     * - Nombre de tâches par statut (TODO, DOING, DONE)
     * - Nombre de tâches en retard (dueDate < now ET status ≠ DONE)
     * - Répartition par priorité
     *
     * SANS ces méthodes, le DashboardService devrait :
     * 1. Charger TOUTES les tâches en mémoire (SELECT * FROM tasks)
     * 2. Itérer en Java pour compter (task.status() == TODO ? count++ : ...)
     *
     * AVEC ces méthodes, on délègue le COUNT à la BDD :
     * → 1 requête SQL COUNT() optimisée au lieu de N résultats chargés en mémoire
     *
     * PRINCIPE DDD : Le port reste le contrat UNIQUE.
     * Le DashboardService injecte TaskPersistencePort et appelle countAll(),
     * countByStatus(), etc. Il ne connaît ni SQL ni JPA.
     *
     * PRINCIPE ISP (Interface Segregation Principle) :
     * On ajoute au port existant plutôt que de créer un "DashboardPort" séparé,
     * car les données comptées sont des tâches — le port de tâches est le bon endroit.
     */

    /**
     * Compte toutes les tâches actives (deletedAt IS NULL).
     *
     * UTILISÉ PAR : DashboardService.getGlobalStats() / getUserStats()
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL
     */
    long countAll();

    /**
     * Compte les tâches actives ayant un statut donné.
     *
     * UTILISÉ PAR : DashboardService pour le pie chart par statut
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND status = :status
     *
     * @param status Le statut à filtrer (TODO, DOING, DONE)
     */
    long countByStatus(Task.TaskStatus status);

    /**
     * Compte les tâches actives par priorité.
     *
     * UTILISÉ PAR : DashboardService pour le bar chart par priorité
     *
     * RETOURNE UN MAP : { "LOW": 5, "MEDIUM": 12, "HIGH": 3, "CRITICAL": 1 }
     * → Chaque clé est le nom de l'enum (TaskPriority.name())
     * → Chaque valeur est le nombre de tâches actives avec cette priorité
     *
     * POURQUOI UN MAP ET PAS 4 MÉTHODES ?
     * → Un seul appel au port = une seule transaction
     * → L'adaptateur peut optimiser (1 requête avec GROUP BY, ou 4 requêtes parallèles)
     *
     * SQL POSSIBLE (optimisé) :
     * SELECT t.priority, COUNT(*) FROM tasks t
     * WHERE t.deleted_at IS NULL GROUP BY t.priority
     */
    Map<String, Long> countByPriority();

    /**
     * Compte les tâches en retard.
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < NOW() → la date d'échéance est dépassée
     * - status ≠ DONE → la tâche n'est pas encore terminée
     * - deletedAt IS NULL → la tâche est active (non archivée)
     *
     * UTILISÉ PAR : DashboardService pour la carte "Tâches en retard"
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL
     *               AND due_date < CURRENT_TIMESTAMP
     *               AND status <> 'DONE'
     */
    long countOverdue();

    /**
     * Compte les tâches actives créées par un utilisateur donné.
     *
     * UTILISÉ PAR : DashboardService.getUserStats(email)
     * pour le Dashboard USER (ne voit que ses propres stats)
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND user_id = :userId
     */
    long countByUserId(String userId);

    /**
     * Compte les tâches actives assignées à un utilisateur donné.
     *
     * UTILISÉ PAR : DashboardService.getUserStats(email)
     * pour le Dashboard USER (compte les tâches qu'on lui a assignées)
     *
     * SQL GÉNÉRÉ : SELECT COUNT(*) FROM tasks
     *               WHERE deleted_at IS NULL AND assignee_id = :assigneeId
     */
    long countByAssigneeId(String assigneeId);

    // ═══════════════════════════════════════════════════════
    // MÉTHODES RBAC-AWARE POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * MÉTHODES RBAC-AWARE — Comptage avec filtre utilisateur optionnel
     * ═══════════════════════════════════════════════════════════════════
     *
     * NOUVEAU CONCEPT — RBAC-AWARE QUERY :
     * ─────────────────────────────────
     * Ces méthodes acceptent un paramètre `username` nullable qui permet
     * de filtrer les résultats selon le rôle de l'utilisateur connecté :
     *
     * ┌────────────────────────────────────────────────────────────────┐
     * │  ADMIN / MANAGER : username = null                             │
     * │  → Le filtre utilisateur est ignoré                           │
     * │  → On compte TOUTES les tâches (vue globale)                  │
     * │                                                                │
     * │  USER : username = "saad@example.com"                         │
     * │  → On filtre par userId = username OR assigneeId = username    │
     * │  → Un USER voit ses tâches CRÉÉES + les tâches ASSIGNÉES      │
     * │                                                                │
     * │  POURQUOI "CRÉÉES + ASSIGNÉES" ?                              │
     * │  → RBAC USER : un utilisateur peut créer des tâches            │
     * │    ET se voir assigner des tâches par un MANAGER.              │
     * │  → Le Dashboard USER doit montrer les DEUX catégories.        │
     * └────────────────────────────────────────────────────────────────┘
     *
     * PRINCIPE DU FILTRAGE OPTIONNEL EN JPQL :
     * ────────────────────────────────────────
     * La clause ":username IS NULL OR condition" est un pattern classique
     * en JPQL qui permet d'ignorer un filtre quand le paramètre est null.
     *
     * Si username = null  : "NULL IS NULL" = TRUE  → court-circuit, filtre ignoré
     * Si username = "x"  : "NULL IS NULL" = FALSE → on évalue la condition
     *
     * PRINCIPE DE SURCHARGE (Method Overloading) :
     * ─────────────────────────────────────────────
     * Java permet d'avoir plusieurs méthodes avec le même nom si les types
     * de paramètres diffèrent. Exemple :
     * - countByStatus(TaskStatus status) → long      (existe déjà)
     * - countByStatus(String username)    → Map       (nouveau)
     *
     * Le compilateur distingue les deux grâce au TYPE du paramètre.
     * Le DashboardController appelle countByStatus(String) car il passe
     * un username (String), jamais un TaskStatus.
     */

    /**
     * Compte les tâches actives, avec filtre RBAC optionnel.
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countActiveTasks(null)  → toutes les tâches actives
     * - USER          : countActiveTasks("email") → tâches créées OU assignées à email
     *
     * SQL GÉNÉRÉ (username = null) :
     * SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     *
     * SQL GÉNÉRÉ (username = "email") :
     * SELECT COUNT(t) FROM tasks t WHERE t.deleted_at IS NULL
     *   AND (t.user_id = 'email' OR t.assignee_id = 'email')
     *
     * @param username L'email de l'utilisateur (null = pas de filtre, vue globale)
     * @return Le nombre de tâches actives (filtrées ou non selon le rôle)
     */
    long countActiveTasks(String username);

    /**
     * Compte les tâches actives par statut, avec filtre RBAC optionnel.
     *
     * SURCHARGE : Cette méthode est une surcharge de countByStatus(TaskStatus).
     * Le compilateur distingue les deux grâce au type du paramètre :
     * - countByStatus(TaskStatus status)  → long  (compte pour UN statut)
     * - countByStatus(String username)    → Map   (compte pour TOUS les statuts, avec RBAC)
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countByStatus(null)  → { "TODO": 10, "DOING": 8, "DONE": 7 }
     * - USER          : countByStatus("email") → stats de l'utilisateur uniquement
     *
     * SQL GÉNÉRÉ :
     * SELECT t.status, COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     * GROUP BY t.status
     *
     * RETOURNE UN MAP :
     * Chaque clé est le nom de l'enum (TODO, DOING, DONE).
     * Si un statut n'a aucune tâche, il n'apparaît PAS dans le Map
     * (comportement normal du GROUP BY SQL).
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Map { "TODO": N, "DOING": N, "DONE": N }
     */
    Map<String, Long> countByStatus(String username);

    /**
     * Compte les tâches actives par priorité, avec filtre RBAC optionnel.
     *
     * SURCHARGE : Cette méthode est une surcharge de countByPriority().
     * - countByPriority()             → Map (comptage global, sans filtre)
     * - countByPriority(String user)  → Map (comptage filtré par utilisateur)
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countByPriority(null)  → { "LOW": 5, "MEDIUM": 12, ... }
     * - USER          : countByPriority("email") → stats de l'utilisateur uniquement
     *
     * SQL GÉNÉRÉ :
     * SELECT t.priority, COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     * GROUP BY t.priority
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Map { "LOW": N, "MEDIUM": N, "HIGH": N, "CRITICAL": N }
     */
    Map<String, Long> countByPriority(String username);

    /**
     * Compte les tâches actives créées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Tâches créées cette semaine"
     * - startOfWeek = LocalDate.now().with(previousOrSame(MONDAY)).atStartOfDay()
     *
     * SQL GÉNÉRÉ (username = null) :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL AND t.created_at >= :after
     *
     * SQL GÉNÉRÉ (username = "email") :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL AND t.created_at >= :after
     *   AND (t.user_id = 'email' OR t.assignee_id = 'email')
     *
     * NOTE : On filtre sur createdAt (pas completedAt) car on veut les tâches
     * CRÉÉES cette semaine, pas celles terminées cette semaine.
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @param after    La date/heure de référence (créées après cette date)
     * @return Le nombre de tâches créées après la date spécifiée
     */
    long countCreatedAfter(String username, LocalDateTime after);

    /**
     * Compte les tâches actives complétées après une date, avec filtre RBAC optionnel.
     *
     * UTILISATION : Dashboard card "Tâches terminées cette semaine"
     *
     * SQL GÉNÉRÉ (username = null) :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL AND t.completed_at >= :after
     *
     * SQL GÉNÉRÉ (username = "email") :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL AND t.completed_at >= :after
     *   AND (t.user_id = 'email' OR t.assignee_id = 'email')
     *
     * NOTE : completedAt est non-null UNIQUEMENT quand status = DONE.
     * Donc cette méthode compte implicitement les tâches DONE terminées après la date.
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @param after    La date/heure de référence (complétées après cette date)
     * @return Le nombre de tâches complétées après la date spécifiée
     */
    long countCompletedAfter(String username, LocalDateTime after);

    /**
     * Compte les tâches en retard, avec filtre RBAC optionnel.
     *
     * DÉFINITION "EN RETARD" :
     * - dueDate < NOW() → la date d'échéance est dépassée
     * - status ≠ DONE → la tâche n'est pas encore terminée
     * - deletedAt IS NULL → la tâche est active (non archivée)
     *
     * NOTE SUR LE NOM : Cette méthode s'appelle countOverdueTasks (avec "Tasks")
     * et non countOverdue, car countOverdue() existe déjà (sans paramètre).
     * C'est un choix de nommage explicite : countOverdueTasks(String) = version RBAC.
     *
     * UTILISATION :
     * - ADMIN/MANAGER : countOverdueTasks(null)  → toutes les tâches en retard
     * - USER          : countOverdueTasks("email") → tâches en retard de l'utilisateur
     *
     * SQL GÉNÉRÉ :
     * SELECT COUNT(t) FROM tasks t
     * WHERE t.deleted_at IS NULL
     *   AND t.due_date < CURRENT_TIMESTAMP
     *   AND t.status <> 'DONE'
     *   AND (:username IS NULL OR t.user_id = :username OR t.assignee_id = :username)
     *
     * @param username L'email de l'utilisateur (null = pas de filtre RBAC)
     * @return Le nombre de tâches en retard (filtrées ou non selon le rôle)
     */
    long countOverdueTasks(String username);
}