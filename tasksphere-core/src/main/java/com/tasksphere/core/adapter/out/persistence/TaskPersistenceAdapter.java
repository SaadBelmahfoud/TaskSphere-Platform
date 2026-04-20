package com.tasksphere.core.adapter.out.persistence;

import com.tasksphere.core.domain.Task;
import com.tasksphere.core.port.out.TaskPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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

        // ── ÉTAPE 1 : Vérifier si l'entité existe déjà en BDD ──
        // On cherche l'entité dans le repository. Deux cas possibles :
        // - Elle est dans le L1 cache → Hibernate la retourne sans SELECT
        // - Elle n'est pas en cache → Hibernate fait un SELECT
        // Dans les deux cas, si l'entité existe, on la récupère MANAGED.
        Optional<TaskEntity> existingEntity = taskRepository.findByIdAndDeletedAtIsNull(task.id());

        if (existingEntity.isPresent()) {
            // ══════════════════════════════════════════════════════════
            //  CHEMIN UPDATE : L'entité existe déjà en BDD
            // ══════════════════════════════════════════════════════════
            //
            // On récupère l'entité MANAGÉE déjà présente dans la Session.
            // C'est la MÊME instance que celle chargée par findByIdAndUserId()
            // dans TaskManager.updateTask() plus tôt dans la transaction.
            //
            // Pourquoi c'est la même instance ?
            // → Parce que le L1 cache garantit l'unicité par identifiant.
            // → Deux appels à findById() avec le même id retournent
            //   la MÊME référence d'objet Java.
            //
            // On modifie ses champs via les setters. Chaque setter met
            // aussi à jour updatedAt automatiquement (voir TaskEntity).
            //
            // Au commit de la transaction, le dirty checking de Hibernate
            // compare l'état actuel avec le snapshot au chargement.
            // Si des champs ont changé → UPDATE SQL automatique.
            TaskEntity managedEntity = existingEntity.get();
            managedEntity.setTitle(task.title());
            managedEntity.setDescription(task.description());
            managedEntity.setStatus(task.status());
            managedEntity.setPriority(task.priority());
            managedEntity.setDueDate(task.dueDate());
            managedEntity.setCompletedAt(task.completedAt());

            // Champs immuables — ON NE LES MODIFIE PAS après création :
            // - createdAt : la date de création ne change jamais
            // - deletedAt : géré uniquement par softDelete()
            // - userId : le propriétaire ne change pas (ownership)
            //
            // Note : updatedAt est mis à jour automatiquement par chaque
            // setter ci-dessus (voir TaskEntity.setTitle(), etc.)

            log.debug("ADAPTATEUR JPA : Mise à jour de la tâche existante '{}'", task.title());
            return managedEntity.toDomain();

        } else {
            // ══════════════════════════════════════════════════════════
            //  CHEMIN INSERT : Nouvelle tâche, pas encore en BDD
            // ══════════════════════════════════════════════════════════
            //
            // L'entité n'existe pas dans la Session ni en BDD.
            // On peut créer un nouveau TaskEntity en toute sécurité.
            //
            // Le constructeur TaskEntity(Task) met isNew = true.
            // repository.save() voit isNew() = true → em.persist() → INSERT.
            //
            // Après l'INSERT, @PostPersist dans TaskEntity met isNew = false.
            // Ainsi, si on appelle save() à nouveau avec cette même entité
            // dans la même transaction, Hibernate saura faire un UPDATE.
            TaskEntity entity = new TaskEntity(task);
            TaskEntity saved = taskRepository.save(entity);
            log.debug("ADAPTATEUR JPA : Nouvelle tâche créée '{}'", task.title());
            return saved.toDomain();
        }
    }

    /**
     * Lister les tâches d'un utilisateur avec pagination.
     *
     * PRINCIPE .map(TaskEntity::toDomain) :
     * Page.map() transforme chaque élément de la page JPA en élément domaine.
     * C'est un mapping fonctionnel (Stream-like) spécifique à Spring Data.
     *
     * PRINCIPE DE FILTRAGE :
     * La méthode de repository filtre automatiquement par userId ET deletedAt IS NULL.
     * Cela signifie que les tâches archivées (soft delete) ne sont jamais retournées.
     */
    @Override
    public Page<Task> findByUserId(String userId, Pageable pageable) {
        log.debug("ADAPTATEUR JPA : Recherche des tâches de l'utilisateur {}", userId);
        return taskRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                .map(TaskEntity::toDomain);
    }

    /**
     * Récupérer une tâche active par son ID.
     *
     * PRINCIPE "Active" :
     * deletedAt IS NULL = la tâche n'a pas été supprimée (soft delete).
     * Les tâches archivées sont ignorées par toutes les requêtes.
     */
    @Override
    public Optional<Task> findById(String id) {
        log.debug("ADAPTATEUR JPA : Recherche de la tâche {}", id);
        return taskRepository.findByIdAndDeletedAtIsNull(id)
                .map(TaskEntity::toDomain);
    }

    /**
     * Récupérer une tâche active appartenant à un utilisateur spécifique.
     *
     * PRINCIPE D'OWNERSHIP (double vérification) :
     * On filtre par id ET userId pour s'assurer que :
     * 1. La tâche existe et est active (deletedAt IS NULL)
     * 2. La tâche appartient bien à l'utilisateur demandeur
     *
     * Cela empêche un utilisateur de modifier/voir les tâches d'un autre.
     * C'est le principe de RBAC (Role-Based Access Control) au niveau data.
     *
     * PRINCIPE DE SÉCURITÉ :
     * Même si l'URL contient un ID valide, si le userId ne correspond pas,
     * la requête retourne Optional.empty() → 404 Not Found.
     * On ne révèle JAMAIS si la tâche existe ou non pour un autre utilisateur.
     */
    @Override
    public Optional<Task> findByIdAndUserId(String id, String userId) {
        log.debug("ADAPTATEUR JPA : Recherche tâche {} pour l'utilisateur {}", id, userId);
        return taskRepository.findByIdAndDeletedAtIsNullAndUserId(id, userId)
                .map(TaskEntity::toDomain);
    }

    /**
     * Soft delete : marque la tâche comme archivée en renseignant deletedAt.
     *
     * PRINCIPE DU SOFT DELETE :
     * On ne supprime JAMAIS physiquement une ligne en BDD. On met
     * simplement deletedAt à la date/heure actuelle. Les requêtes
     * filtrent sur "deletedAt IS NULL" pour ne retourner que les actives.
     *
     * AVANTAGES DU SOFT DELETE :
     * 1. Récupération possible en cas d'erreur
     * 2. Audit trail : on sait quand la tâche a été "supprimée"
     * 3. Conformité RGPD : on garde une trace des données
     * 4. Pas de CASCADE DELETE qui pourrait supprimer d'autres données
     *
     * PRINCIPE .ifPresent() :
     * On ne fait le soft delete que si la tâche existe et est active.
     * Si la tâche n'existe pas ou est déjà supprimée, on ne fait rien.
     *
     * PRINCIPE DU SETTER :
     * On utilise entity.setDeletedAt() au lieu d'accéder directement au champ.
     * Le setter met aussi à jour updatedAt automatiquement (voir TaskEntity).
     *
     * PRINCIPE DE SÉCURITÉ ICI :
     * Cette méthode ne vérifie PAS l'userId ! C'est le TaskManager.deleteTask()
     * qui appelle d'abord findByIdAndUserId() pour vérifier l'ownership
     * AVANT d'appeler softDelete(). L'adaptateur ne fait que la technique.
     */
    @Override
    public void softDelete(String id) {
        log.debug("ADAPTATEUR JPA : Soft delete de la tâche {}", id);
        taskRepository.findByIdAndDeletedAtIsNull(id).ifPresent(entity -> {
            entity.setDeletedAt(java.time.LocalDateTime.now());
            taskRepository.save(entity);
        });
    }
}