package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/*
 * ====================================================================
 * ADAPTATEUR DE PERSISTANCE (Pont Domaine ↔ JPA)
 * ====================================================================
 *
 * PRINCIPE D'ARCHITECTURE HEXAGONALE :
 * L'adaptateur implémente le Port (interface du domaine) et traduit
 * les appels en opérations JPA concrètes.
 *
 * Schéma de flux :
 * Controller → Service (TaskManager) → Port (TaskPersistencePort) → Adaptateur (THIS)
 *                                                                       ↓
 *                                                                  Repository (JPA)
 *                                                                       ↓
 *                                                                    Base de données
 *
 * Le domaine n'a AUCUNE idée que JPA existe. Il ne voit que l'interface.
 * Si on change de BDD demain (PostgreSQL, MongoDB), seul cet adaptateur change.
 * Le service et le domaine restent identiques → zéro impact.
 *
 * PRINCIPE @Component :
 * Spring détecte automatiquement cette classe et la crée comme bean.
 * Spring l'injecte dans le TaskManager qui dépend de TaskPersistencePort.
 * C'est le mécanisme d'injection de dépendance (DI).
 *
 * PRINCIPE DE CONVERSION :
 * - Domaine → JPA : new TaskEntity(task) dans save() (INSERT uniquement)
 * - JPA → Domaine : entity.toDomain() dans les méthodes de lecture
 * Ces conversions sont MANUELLES et EXPLICITES pour garder le contrôle total.
 *
 * RAPPEL IMPORTANT SUR LA SESSION HIBERNATE :
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ La Session Hibernate (ou Persistence Context) est un cache L1    │
 * │ qui stocke toutes les entités chargées pendant une transaction.  │
 * │                                                                  │
 * │ RÈGLE D'OR : Il ne peut y avoir qu'UN SEUL objet par identifiant│
 * │ dans la Session. Si tu essaies d'ajouter un 2ème objet avec le   │
 * │ même id → NonUniqueObjectException !                             │
 * │                                                                  │
 * │ Quand tu modifies les champs d'une entité MANAGED, Hibernate     │
 * │ détecte automatiquement les changements (dirty checking) et      │
 * │ génère l'UPDATE SQL au commit de la transaction.                 │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPersistenceAdapter implements TaskPersistencePort {

    private final TaskRepository taskRepository;

    /**
     * Sauvegarder une tâche (création ou mise à jour).
     *
     * FLUX : Task (domaine) → TaskEntity (JPA) → BDD
     *
     * ╔══════════════════════════════════════════════════════════════════╗
     * ║  PRINCIPE DU DOUBLE CHEMIN (INSERT vs UPDATE)                   ║
     * ╠══════════════════════════════════════════════════════════════════╣
     * ║                                                                  ║
     * ║  CRÉATION (INSERT) :                                             ║
     * ║  1. L'entité n'existe pas en BDD                                 ║
     * ║  2. On crée un nouveau TaskEntity(task) avec isNew = true         ║
     * ║  3. repository.save() → em.persist() → INSERT SQL                 ║
     * ║                                                                  ║
     * ║  MISE À JOUR (UPDATE) :                                           ║
     * ║  1. L'entité existe déjà en BDD                                  ║
     * ║  2. On récupère l'entité MANAGÉE depuis le repository             ║
     * ║  3. On modifie ses champs via les setters                        ║
     * ║  4. Dirty Checking de Hibernate → UPDATE SQL au commit            ║
     * ║                                                                  ║
     * ╚══════════════════════════════════════════════════════════════════╝
     *
     * ╔══════════════════════════════════════════════════════════════════╗
     * ║  POURQUOI ON NE FAIT PAS "new TaskEntity(task)" POUR UPDATE ?   ║
     * ╠══════════════════════════════════════════════════════════════════╣
     * ║                                                                  ║
     * ║  Bug NonUniqueObjectException (AVANT la correction) :            ║
     * ║                                                                  ║
     * ║  1. updateTask() appelle findByIdAndUserId()                      ║
     * ║     → Hibernate charge l'entité dans son "Session" (L1 cache)    ║
     * ║     → L'entité est maintenant MANAGÉE (traquée par Hibernate)     ║
     * ║                                                                  ║
     * ║  2. save() crée un NOUVEAU TaskEntity(task) avec                  ║
     * ║     le MÊME id mais isNew = true                                 ║
     * ║                                                                  ║
     * ║  3. repository.save() fait em.persist() (car isNew=true)         ║
     * ║     → persist() essaie d'ajouter l'entité à la Session           ║
     * ║                                                                  ║
     * ║  4. Hibernate voit 2 objets avec le même id dans la Session      ║
     * ║     → 💥 NonUniqueObjectException !                              ║
     * ║                                                                  ║
     * ║  SOLUTION APPLIQUÉE :                                             ║
     * ║  Récupérer l'entité MANAGÉE déjà présente dans la Session         ║
     * ║  et modifier ses champs via les setters.                         ║
     * ║  Hibernate détecte les changements automatiquement               ║
     * ║  (dirty checking) et génère l'UPDATE SQL au commit.              ║
     * ║                                                                  ║
     * ╚══════════════════════════════════════════════════════════════════╝
     *
     * PRINCIPE DU DIRTY CHECKING (rappel) :
     * Hibernate compare l'état actuel de l'entité managée avec son état
     * au moment du chargement (snapshot). Si des champs ont changé,
     * il génère automatiquement un UPDATE SQL à la fin de la transaction
     * (avant le commit). C'est pour ça qu'on n'a pas besoin d'appeler
     * explicitement repository.save() pour les entités managées quand
     * on modifie leurs champs — Hibernate s'en occupe au flush.
     *
     * PRINCIPE DU L1 CACHE (rappel) :
     * Quand on appelle taskRepository.findByIdAndDeletedAtIsNull(task.id()),
     * Hibernate regarde D'ABORD dans son L1 cache. Si l'entité y est
     * déjà (parce qu'elle a été chargée plus tôt dans la même transaction
     * par findByIdAndUserId()), Hibernate retourne cette MÊME instance
     * sans faire de SELECT supplémentaire. C'est le first-level cache.
     */
    @Override
    public Task save(Task task) {
        log.debug("ADAPTATEUR JPA : Sauvegarde de la tâche '{}' (id: {})", task.title(), task.id());

        Optional<TaskEntity> existingEntity = taskRepository.findByIdAndDeletedAtIsNull(task.id());

        if (existingEntity.isPresent()) {
            // ══════════════════════════════════════════════════════════
            //  CHEMIN UPDATE : L'entité existe déjà en BDD
            // ══════════════════════════════════════════════════════════
            TaskEntity managedEntity = existingEntity.get();
            managedEntity.setTitle(task.title());
            managedEntity.setDescription(task.description());
            managedEntity.setStatus(task.status());
            managedEntity.setPriority(task.priority());
            managedEntity.setDueDate(task.dueDate());
            managedEntity.setCompletedAt(task.completedAt());
            // CORRECTION : Ajout du setAssigneeId pour persister l'assignation
            managedEntity.setAssigneeId(task.assigneeId());

            // Champs immuables — ON NE LES MODIFIE PAS après création :
            // - createdAt : la date de création ne change jamais
            // - deletedAt : géré uniquement par softDelete()
            // - userId : le propriétaire ne change pas (ownership)

            log.debug("ADAPTATEUR JPA : Mise à jour de la tâche existante '{}'", task.title());
            return managedEntity.toDomain();

        } else {
            // ══════════════════════════════════════════════════════════
            //  CHEMIN INSERT : Nouvelle tâche, pas encore en BDD
            // ══════════════════════════════════════════════════════════
            TaskEntity entity = new TaskEntity(task);
            TaskEntity saved = taskRepository.save(entity);
            log.debug("ADAPTATEUR JPA : Nouvelle tâche créée '{}'", task.title());
            return saved.toDomain();
        }
    }

    @Override
    public Page<Task> findByUserId(String userId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {}", userId);
        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Optional<Task> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche de la tâche {}", id);
        return taskRepository.findByIdAndDeletedAtIsNull(id)
                .map(TaskEntity::toDomain);
    }

    @Override
    public Optional<Task> findByIdAndUserId(String id, String userId) {
        log.debug("ADAPTATEUR JPA : Recherche tâche {} pour l'utilisateur {}", id, userId);
        return taskRepository.findByIdAndDeletedAtIsNullAndUserId(id, userId)
                .map(TaskEntity::toDomain);
    }

    @Override
    public void softDelete(String id) {
        log.debug("ADAPTATEUR JPA : Soft delete de la tâche {}", id);
        taskRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.setDeletedAt(java.time.LocalDateTime.now());
            taskRepository.save(entity);
        });
    }

    // ═══════════════════════════════════════════════════════
    // RECHERCHE DYNAMIQUE AVEC FILTRES (Sprint 2)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * RECHERCHE DYNAMIQUE AVEC FILTRES OPTIONNELS
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : Traduction du Parameter Object vers le Repository
     * ─────────────────────────────────────────────────────────
     * Cette méthode reçoit un TaskSearchCriteria (Parameter Object)
     * et décompose chaque champ pour le passer au @Query JPQL
     * du TaskRepository.
     *
     * FLUX COMPLET :
     * TaskManager.searchTasks(criteria, ...)
     *   → TaskPersistencePort.searchTasks(criteria, pageable)  [interface]
     *     → TaskPersistenceAdapter.searchTasks(criteria, pageable) [CETTE MÉTHODE]
     *       → TaskRepository.searchTasks(keyword, userId, ..., pageable) [JPA @Query]
     *         → SELECT ... FROM TaskEntity WHERE deletedAt IS NULL
     *           AND (:param IS NULL OR condition)
     *
     * PRINCIPE ":param IS NULL OR condition" DANS LE @Query :
     * ────────────────────────────────────────────────────────
     * Quand un paramètre est null :
     *   (:param IS NULL)  → TRUE  → le filtre est ignoré (court-circuit)
     *
     * Quand un paramètre est non-null :
     *   (:param IS NULL)  → FALSE → la condition après OR est évaluée
     *
     * EXEMPLE CONCRET avec keyword = "urgence" et status = null :
     *   AND (NULL IS NULL                           → TRUE  → ignoré)
     *   AND (:status IS NULL OR t.status = :status)  → TRUE  → ignoré)
     *
     *   → Résultat SQL : WHERE deletedAt IS NULL
     *       AND (title LIKE '%urgence%' OR description LIKE '%urgence%')
     *
     * @param criteria Les critères de recherche (tous optionnels)
     * @param pageable La pagination et le tri
     * @return Une page de Task (objets domaine, pas des entités JPA)
     */
    @Override
    public Page<Task> searchTasks(TaskSearchCriteria criteria, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche dynamique avec critères keyword={}, userId={}, assigneeId={}, status={}, priority={}",
                criteria.keyword(), criteria.userId(), criteria.assigneeId(),
                criteria.status(), criteria.priority());

        // Déstructure le Parameter Object en paramètres individuels pour JPQL
        Page<TaskEntity> result = taskRepository.searchTasks(
                criteria.keyword(),
                criteria.userId(),
                criteria.assigneeId(),
                criteria.status(),
                criteria.priority(),
                criteria.dueDateFrom(),
                criteria.dueDateTo(),
                criteria.createdFrom(),
                criteria.createdTo(),
                pageable
        );

        // .map(TaskEntity::toDomain) : transforme chaque TaskEntity en Task (record domaine)
        // Les métadonnées de pagination (totalElements, totalPages) sont préservées
        return result.map(TaskEntity::toDomain);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES DE COMPTAGE POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════
     * COMPTAGE POUR DASHBOARD — Section 6 : Collaboration
     * ═══════════════════════════════════════════════════════════
     *
     * PRINCIPE : L'adaptateur traduit les méthodes de comptage du port
     * en appels au Repository JPA.
     *
     * AVANTAGE vs itération en mémoire :
     * - countAll() → 1 requête COUNT(*) au lieu de SELECT * + .size()
     * - countByStatus() → 1 COUNT avec WHERE au lieu de filtre Java
     * - countByPriority() → 1 GROUP BY au lieu de 4 boucles
     *
     * POURQUOI PAS DE CONVERSION ENTITY → DOMAIN ?
     * → Les méthodes de comptage retournent des primitives (long)
     *   ou des Map<String, Long>. Il n'y a PAS de mapping Entity ↔ Domain
     *   à faire. C'est un avantage des COUNT : on ne charge aucune entité.
     *
     * PERFORMANCE :
     * ┌─────────────────────────────────────────────────────────────┐
     * │  SANS count (itération) :                                   │
     * │  SELECT * FROM tasks WHERE deleted_at IS NULL               │
     * │  → Charge N entités complètes en mémoire                     │
     * │  → Pour 10 000 tâches : ~10 MB en mémoire JVM               │
     * │                                                              │
     * │  AVEC count (méthodes dédiées) :                            │
     * │  SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL         │
     * │  → Retourne un seul long (8 octets)                          │
     * │  → Pour 10 000 tâches : 8 octets en mémoire JVM             │
     * └─────────────────────────────────────────────────────────────┘
     */

    @Override
    public long countAll() {
        log.debug("ADAPTATEUR JPA : Comptage de toutes les tâches actives");
        return taskRepository.countByDeletedAtIsNull();
    }

    @Override
    public long countByStatus(Task.TaskStatus status) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches avec statut {}", status);
        return taskRepository.countByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public Map<String, Long> countByPriority() {
        log.debug("ADAPTATEUR JPA : Comptage des tâches par priorité");

        /**
         * ═══════════════════════════════════════════════════════════
         * TRANSFORMATION Object[] → Map<String, Long>
         * ═══════════════════════════════════════════════════════════
         *
         * Le @Query GROUP BY retourne une List<Object[]> :
         * - row[0] = TaskPriority (enum) ex: HIGH
         * - row[1] = Long (count) ex: 3
         *
         * On transforme en Map<String, Long> :
         * { "HIGH": 3, "MEDIUM": 12, "LOW": 5, "CRITICAL": 1 }
         *
         * POURQUOI UN LinkedHashMap ?
         * → Préserve l'ordre d'insertion (contrairement à HashMap)
         * → L'ordre des priorités sera celui retourné par la BDD
         *   (généralement l'ordre de déclaration de l'enum en JPQL)
         *
         * NOTE : Si une priorité n'a aucune tâche, elle n'apparaîtra
         * PAS dans le résultat GROUP BY. Le DashboardService doit
         * gérer les clés manquantes (afficher 0 par défaut).
         */
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByPriority()) {
            Task.TaskPriority priority = (Task.TaskPriority) row[0];
            Long count = (Long) row[1];
            result.put(priority.name(), count);
        }
        return result;
    }

    @Override
    public long countOverdue() {
        log.debug("ADAPTATEUR JPA : Comptage des tâches en retard");
        return taskRepository.countOverdue();
    }

    @Override
    public long countByUserId(String userId) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches de l'utilisateur {}", userId);
        return taskRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public long countByAssigneeId(String assigneeId) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches assignées à {}", assigneeId);
        return taskRepository.countByAssigneeIdAndDeletedAtIsNull(assigneeId);
    }

    // ═══════════════════════════════════════════════════════
    // MÉTHODES RBAC-AWARE POUR LE DASHBOARD (Sprint 3 — Section 6)
    // ═══════════════════════════════════════════════════════

    /**
     * ═══════════════════════════════════════════════════════════════════
     * IMPLÉMENTATION RBAC-AWARE — Comptage avec filtre utilisateur
     * ═══════════════════════════════════════════════════════════════════
     *
     * PRINCIPE : Ces méthodes implémentent les 6 nouvelles signatures
     * du port TaskPersistencePort. Elles délèguent au Repository JPA
     * qui exécute les @Query avec le pattern ":username IS NULL OR".
     *
     * FLUX COMPLET :
     * DashboardController.getDashboardStats()
     *   → taskPersistencePort.countActiveTasks(username)       [port — interface]
     *     → TaskPersistenceAdapter.countActiveTasks(username)   [CETTE CLASSE]
     *       → taskRepository.countActiveTasks(username)         [Spring Data JPA]
     *         → @Query SQL avec filtre optionnel                [BDD]
     *
     * RÔLE DE L'ADAPTATEUR ICI :
     * ──────────────────────────
     * L'adaptateur fait principalement de la "pass-through" (transfert direct)
     * car les @Query JPA retournent déjà le bon type (long ou List<Object[]>).
     *
     * Cependant, pour les méthodes GROUP BY (countByStatus, countByPriority),
     * l'adaptateur transforme la List<Object[]> en Map<String, Long>.
     * C'est une LOGIQUE D'ADAPTATION légitime :
     * - Le Repository retourne des projections brutes (Object[])
     * - Le Port définit le contrat métier (Map<String, Long>)
     * - L'adaptateur fait la traduction
     */

    /**
     * Compte les tâches actives avec filtre RBAC optionnel.
     *
     * Délègue directement au Repository : la @Query gère le filtre.
     *
     * @param username null = vue globale (ADMIN/MANAGER), email = filtre USER
     * @return Nombre de tâches actives (filtrées ou non)
     */
    @Override
    public long countActiveTasks(String username) {
        log.debug("ADAPTATEUR JPA : Comptage des tâches actives (RBAC username={})",
                username != null ? username : "GLOBAL");
        return taskRepository.countActiveTasks(username);
    }

    /**
     * Compte les tâches actives par statut avec filtre RBAC optionnel.
     *
     * TRANSFORMATION Object[] → Map<String, Long> :
     * ──────────────────────────────────────────
     * Le Repository retourne List<Object[]> depuis le GROUP BY.
     * Chaque Object[] = [TaskStatus enum, Long count].
     * L'adaptateur transforme en Map<String, Long> pour le contrat du port.
     *
     * @param username null = vue globale, email = filtre USER
     * @return Map { "TODO": N, "DOING": N, "DONE": N }
     */
    @Override
    public Map<String, Long> countByStatus(String username) {
        log.debug("ADAPTATEUR JPA : Comptage par statut (RBAC username={})",
                username != null ? username : "GLOBAL");

        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByStatus(username)) {
            Task.TaskStatus status = (Task.TaskStatus) row[0];
            Long count = (Long) row[1];
            result.put(status.name(), count);
        }
        return result;
    }

    /**
     * Compte les tâches actives par priorité avec filtre RBAC optionnel.
     *
     * TRANSFORMATION Object[] → Map<String, Long> :
     * Même pattern que countByStatus(String) mais pour les priorités.
     *
     * @param username null = vue globale, email = filtre USER
     * @return Map { "LOW": N, "MEDIUM": N, "HIGH": N, "CRITICAL": N }
     */
    @Override
    public Map<String, Long> countByPriority(String username) {
        log.debug("ADAPTATEUR JPA : Comptage par priorité (RBAC username={})",
                username != null ? username : "GLOBAL");

        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupByPriorityFiltered(username)) {
            Task.TaskPriority priority = (Task.TaskPriority) row[0];
            Long count = (Long) row[1];
            result.put(priority.name(), count);
        }
        return result;
    }

    /**
     * Compte les tâches actives créées après une date avec filtre RBAC optionnel.
     *
     * Délègue directement au Repository : la @Query gère le filtre date + RBAC.
     *
     * @param username null = vue globale, email = filtre USER
     * @param after    Date de référence (créées après cette date)
     * @return Nombre de tâches créées après la date
     */
    @Override
    public long countCreatedAfter(String username, LocalDateTime after) {
        log.debug("ADAPTATEUR JPA : Comptage tâches créées après {} (RBAC username={})",
                after, username != null ? username : "GLOBAL");
        return taskRepository.countCreatedAfter(username, after);
    }

    /**
     * Compte les tâches actives complétées après une date avec filtre RBAC optionnel.
     *
     * Délègue directement au Repository : la @Query gère le filtre date + RBAC.
     *
     * NOTE : completedAt est non-null uniquement pour les tâches DONE.
     * Cette méthode ne compte donc que les tâches terminées.
     *
     * @param username null = vue globale, email = filtre USER
     * @param after    Date de référence (complétées après cette date)
     * @return Nombre de tâches complétées après la date
     */
    @Override
    public long countCompletedAfter(String username, LocalDateTime after) {
        log.debug("ADAPTATEUR JPA : Comptage tâches complétées après {} (RBAC username={})",
                after, username != null ? username : "GLOBAL");
        return taskRepository.countCompletedAfter(username, after);
    }

    /**
     * Compte les tâches en retard avec filtre RBAC optionnel.
     *
     * Délègue directement au Repository : la @Query gère le filtre RBAC
     * + les conditions "en retard" (dueDate < now ET status ≠ DONE).
     *
     * @param username null = vue globale, email = filtre USER
     * @return Nombre de tâches en retard (filtrées ou non)
     */
    @Override
    public long countOverdueTasks(String username) {
        log.debug("ADAPTATEUR JPA : Comptage tâches en retard (RBAC username={})",
                username != null ? username : "GLOBAL");
        return taskRepository.countOverdueTasks(username);
    }
}