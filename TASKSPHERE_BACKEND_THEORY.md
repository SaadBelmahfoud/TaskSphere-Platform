# 📘 TaskSphere-Platform — Guide Théorique Complet du Backend

> Document de référence pour comprendre tous les principes architecturaux, patterns de conception
> et fonctionnement technique du backend TaskSphere-Platform (Spring Boot 3 + Architecture Hexagonale).

---

## Table des Matières

1. [Architecture Hexagonale (Ports & Adapters)](#1-architecture-hexagonale)
2. [Inversion de Dépendance & IoC](#2-inversion-de-dépendance--ioc)
3. [Authentification JWT (JSON Web Token)](#3-authentification-jwt)
4. [Spring Security — Chaîne de Filtres](#4-spring-security--chaîne-de-filtres)
5. [RBAC — Contrôle d'Accès Basé sur les Rôles](#5-rbac--contrôle-daccès-basé-sur-les-rôles)
6. [JPA / Hibernate — Persistence](#6-jpa--hibernate--persistence)
7. [Hibernate Session, L1 Cache & Dirty Checking](#7-hibernate-session-l1-cache--dirty-checking)
8. [Soft Delete vs Hard Delete](#8-soft-delete-vs-hard-delete)
9. [Recherche Dynamique avec JPQL](#9-recherche-dynamique-avec-jpql)
10. [Pattern Repository avec Spring Data JPA](#10-pattern-repository-avec-spring-data-jpa)
11. [Domain-Driven Design — Aggregat & Immuabilité](#11-domain-driven-design--agregat--immutabilité)
12. [Lombok — Réduction du Code Boilerplate](#12-lombok--réduction-du-code-boilerplate)
13. [Gestion des Tokens — Dual Token Architecture](#13-gestion-des-tokens--dual-token-architecture)
14. [Assignation de Tâches]((#14-assignation-de-tâches)
15. [Configuration & Environnement](#15-configuration--environnement)
16. [Console H2 — Administration Base de Données]((#16-console-h2--administration-base-de-données)
17. [Diagramme de Flux Complet](#17-diagramme-de-flux-complet)

---

## 1. Architecture Hexagonale

### Concept Fondamental

L'architecture hexagonale (aussi appelée "Ports & Adapters") est un pattern architectural qui **isole le domaine métier** de toute dépendance technologique (base de données, framework web, messages).

### Structure de TaskSphere

```
┌─────────────────────────────────────────────────────────────┐
│                     ADAPTATEURS ENTRÉE                       │
│   Contrôleurs REST, Événements, Messages                     │
│                                                              │
│   ┌─────────────────────────────────────────────────┐        │
│   │            PORT ENTRÉE (Interface)                │        │
│   │   TaskPersistencePort, EventPublisherPort,       │        │
│   │   UserInformationPort, IamQueryService            │        │
│   └──────────────────┬──────────────────────────────┘        │
│                      │                                       │
│ ┌────────────────────▼──────────────────────────────────┐    │
│ │              DOMAINE (Cœur métier)                      │    │
│ │                                                         │    │
│ │   Task (record immutable)                               │    │
│ │   TaskManager (service métier @Transactional)           │    │
│ │   TaskCreatedEvent (event domaine)                      │    │
│ │                                                         │    │
│ │   Règles : immuabilité, factory methods,               │    │
│ │   transitions de statut, soft delete                    │    │
│ └────────────────────┬──────────────────────────────────┘    │
│                      │                                       │
│   ┌──────────────────▼──────────────────────────────┐        │
│   │            PORT SORTIE (Interface)                │        │
│   │   TaskPersistencePort (sauvegarde)                │        │
│   │   EventPublisherPort (publication événements)     │        │
│   │   UserInformationPort (infos utilisateur)         │        │
│   └──────────────────┬──────────────────────────────┘        │
│                      │                                       │
├──────────────────────┼───────────────────────────────────────┤
│                     ADAPTATEURS SORTIE                         │
│                                                              │
│   TaskPersistenceAdapter → TaskRepository (JPA/Hibernate)    │
│   SpringEventPublisherAdapter → ApplicationEventPublisher    │
│   IamUserAdapter → IamQueryService (appel in-memory)          │
└─────────────────────────────────────────────────────────────┘
```

### Pourquoi cette architecture ?

- **Isolation** : Le cœur ne dépend d'aucune technologie. Pas de `@Entity`, pas de `@RestController` dans le domaine.
- **Testabilité** : On peut mocker les ports pour tester `TaskManager` en isolation complète.
- **Évolutivité** : Changer de base de données = changer `TaskPersistenceAdapter`, pas le domaine.
- **Compréhension** : Le flux est toujours le même : Controller → Service → Port → Adaptateur.

### Modules Maven

```
tasksphere-platform/          ← Parent POM (dependencies communes)
├── tasksphere-iam/            ← Module IAM (authentification, users)
│   ├── adapter/in/            ← AuthController, IamQueryController
│   ├── adapter/out/           ← (pas dans IAM, mais UserRepository est un port)
│   ├── config/security/       ← SecurityConfig, JwtService, JwtAuthFilter
│   ├── domain/                ← UserEntity, RefreshTokenEntity
│   ├── dto/                   ← RegisterRequest, IamUserDto
│   └── port/                  ← Interfaces (IamQueryService, UserRepository)
└── tasksphere-core/           ← Module Core (tâches)
    ├── adapter/in/            ← TaskController
    ├── adapter/out/persistence/ ← TaskEntity, TaskRepository, TaskPersistenceAdapter
    ├── adapter/out/iam/       ← IamUserAdapter (bridge vers IAM)
    ├── adapter/out/messaging/ ← SpringEventPublisherAdapter
    ├── controller/            ← TaskController
    ├── domain/                ← Task (record), TaskCreatedEvent
    ├── dto/                   ← TaskCreateRequest, TaskResponse, etc.
    ├── exception/             ← GlobalExceptionHandler
    ├── port/out/              ← Interfaces (TaskPersistencePort, etc.)
    └── service/               ← TaskManager (cœur métier)
```

---

## 2. Inversion de Dépendance & IoC

### Principe SOLID — Dependency Inversion

Le domaine (Task, TaskManager) définit des **interfaces** (ports) que l'infrastructure doit implémenter. Le domaine ne dépend JAMAIS de l'infrastructure.

```
AVANT (mauvais) :                    APRÈS (bon) :
TaskManager → TaskRepository        TaskManager → TaskPersistencePort (interface)
(taskManager dépend de JPA)          TaskPersistenceAdapter → implémente TaskPersistencePort
                                     (l'adaptateur dépend du domaine, pas l'inverse)
```

### Spring IoC Container

Spring gère la création et l'injection des beans via le conteneur IoC :

```java
// Spring crée le bean et l'injecte automatiquement
@RequiredArgsConstructor  // Lombok génère le constructeur avec tous les champs `final`
@Service
public class TaskManager {
    private final TaskPersistencePort persistencePort;  // Injecté par Spring
    private final EventPublisherPort eventPublisher;    // Injecté par Spring
}
```

### @ComponentScan

Le `TasksphereCoreApplication` centralise les scans :

```java
@ComponentScan(basePackages = {"com.tasksphere.core", "com.tasksphere.iam"})
@EnableJpaRepositories(basePackages = {"com.tasksphere.core.adapter.out.persistence", "com.tasksphere.iam.port.out"})
@EntityScan(basePackages = {"com.tasksphere.core.adapter.out.persistence", "com.tasksphere.iam.domain"})
```

**Pourquoi ces scans explicites ?** Car on a un monolithe modulaire : Spring doit savoir où chercher les composants, repositories et entités dans chaque module.

---

## 3. Authentification JWT

### Qu'est-ce qu'un JWT ?

Un JWT (JSON Web Token) est un token signé cryptographiquement composé de 3 parties séparées par des points :

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGVtYWlsLmNvbSIsInJvbGUiOiJVU0VSIiwiaWF0IjoxNzE0MDAsImV4cCI6MTcxNDAzNjAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
|_________Header_________| |___________________Payload___________________| |___Signature___|
 Base64                  Base64                                          HMAC-SHA256
```

| Partie | Contenu | Encodage |
|--------|---------|----------|
| **Header** | Algorithme (`HS256`) et type (`JWT`) | Base64 |
| **Payload** | Données : `sub` (email), `role`, `iat` (création), `exp` (expiration) | Base64 |
| **Signature** | Preuve cryptographique que le token n'a pas été modifié | HMAC-SHA256 |

### Flux d'authentification complet

```
1. CONNEXION (POST /api/v1/auth/login)
   ┌────────┐  {email, password}  ┌──────────────┐
   │ Client │ ─────────────────→ │AuthController │
   └────────┘                     └──────┬───────┘
                                        │
                                 ┌──────▼───────┐
                                 │ Vérifie avec │
                                 │ BCrypt       │
                                 │ passwordEncoder│
                                 │ .matches()   │
                                 └──────┬───────┘
                                        │ OK
                                 ┌──────▼───────┐
                                 │  JwtService  │
                                 │  Crée JWT :  │
                                 │  sub: email  │
                                 │  role: USER  │
                                 │  exp: +1h    │
                                 │  sign: HMAC  │
                                 └──────┬───────┘
                                        │
                                 ┌──────▼───────────┐
                                 │RefreshTokenService│
                                 │ Crée UUID opaque │
                                 │ Hash + stocke BDD│
                                 └──────┬───────────┘
                                        │
   ┌────────┐  {accessToken, refreshToken}  ┌──────────┐
   │ Client │ ←──────────────────────────── │ Réponse │
   └────────┘                                └──────────┘

2. REQUÊTE AUTHENTIFIÉE (GET /api/v1/tasks)
   ┌────────┐  Authorization: Bearer eyJ...  ┌────────────────────┐
   │ Client │ ──────────────────────────→ │JwtAuthenticationFilter│
   └────────┘                              └──────┬─────────────┘
                                                 │
                                          ┌──────▼───────┐
                                          │ JwtService  │
                                          │ extractUsername()
                                          │ isTokenValid()│
                                          └──────┬───────┘
                                                 │ OK
                                          ┌──────▼─────────────┐
                                          │CustomUserDetails  │
                                          │Service             │
                                          │ loadUserByUsername()│
                                          └──────┬─────────────┘
                                                 │
                                          ┌──────▼─────────────┐
                                          │SecurityContextHolder│
                                          │ setAuthentication()│
                                          │ (ThreadLocal)       │
                                          └──────┬─────────────┘
                                                 │
                                          ┌──────▼─────────────┐
                                          │  TaskController    │
                                          │ authentication    │
                                          │  .getName()        │
                                          │  → "email@..."    │
                                          └───────────────────┘

3. REFRESH TOKEN (POST /api/v1/auth/refresh)
   ┌────────┐  {refreshToken}  ┌────────────────────┐
   │ Client │ ──────────────→ │AuthController       │
   └────────┘                   └──────┬─────────────┘
                                       │
                                ┌──────▼─────────────┐
                                │RefreshTokenService │
                                │ verifyRefreshToken()│ ← BCrypt matches
                                │                     │   sur TOUS les tokens
                                └──────┬─────────────┘
                                       │ Valide
                                ┌──────▼─────────────┐
                                │ ROTATION :          │
                                │ 1. Révoque l'ancien │
                                │ 2. Crée un nouveau  │
                                │ 3. Génère nouveau JWT│
                                └───────────────────┘
```

### Propriétés du JWT dans TaskSphere

| Propriété | Valeur |
|-----------|-------|
| Algorithme | HMAC-SHA256 |
| Durée access token | 1 heure (3600s) |
| Durée refresh token | 7 jours |
| Stockage refresh token | Base H2, hashé avec BCrypt |
| Rotation des tokens | Oui (révocation de l'ancien à chaque refresh) |
| Claims personnalisés | `role` (USER/MANAGER/ADMIN) |

---

## 4. Spring Security — Chaîne de Filtres

### Chaîne de filtres

Chaque requête HTTP passe par une chaîne de filtres avant d'arriver au contrôleur :

```
Requête HTTP
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. CorsFilter                                                   │
│    Vérifie l'origine CORS (localhost:3000 autorisé)             │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. CsrfFilter → DÉSACTIVÉ                                       │
│    On utilise des JWT stateless, pas de sessions → pas de CSRF │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. JwtAuthenticationFilter (NOTRE FILTRE PERSONNALISÉ)           │
│    - Extrait le header "Authorization: Bearer <token>"          │
│    - Vérifie la signature avec la clé secrète                  │
│    - Vérifie l'expiration                                       │
│    - Charge l'utilisateur via UserDetailsService                │
│    - Place l'Authentication dans SecurityContextHolder           │
│    - Si erreur : rejet silencieux (pas de 401 explicite)       │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Authorization Filter                                         │
│    - Vérifie les règles d'autorisation                         │
│    - /api/v1/auth/** → permitAll (public)                      │
│    - /h2-console/** → hasRole('ADMIN')                         │
│    - /swagger-ui/** → permitAll (documentation)                │
│    - Tout le reste → authenticated                              │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Controller (TaskController, AuthController, etc.)            │
│    - Authentication authentication = SecurityContextHolder          │
│      .getContext().getAuthentication()                          │
│    - authentication.getName() → email de l'utilisateur          │
│    - authentication.getAuthorities() → ["ROLE_USER"]            │
└─────────────────────────────────────────────────────────────────┘
```

### SecurityConfig — Explication

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
- **STATELESS** = pas de HttpSession. Chaque requête est indépendante.
- L'état d'authentification est porté par le JWT dans le header Authorization.
- C'est le choix standard pour les APIs REST avec JWT.

```java
.csrf(AbstractHttpConfigurer::disable)
```
- CSRF (Cross-Site Request Forgery) protège les formulaires HTML.
- Avec des JWT stateless, il n'y a pas de cookies de session → CSRF inutile.
- Le désactiver évite les erreurs 403 sur les requêtes PUT/PATCH/DELETE.

```java
.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
```
- Autorise les iframes pour la console H2 (qui s'affiche dans une iframe).

---

## 5. RBAC — Contrôle d'Accès Basé sur les Rôles

### Modèle RBAC de TaskSphere

```
┌─────────┐      ┌─────────┐      ┌─────────┐
│  USER   │      │ MANAGER │      │  ADMIN  │
└────┬────┘      └────┬────┘      └────┬────┘
     │                │                │
     └────────────────┼────────────────┘
                      │
              ┌───────▼────────┐
              │   PERMISSIONS   │
              └────────────────┘
```

### Matrice des Permissions

| Action | USER | MANAGER | ADMIN |
|--------|------|---------|-------|
| Créer une tâche | ✅ (ses tâches) | ✅ | ✅ |
| Voir ses tâches | ✅ | ✅ | ✅ |
| Voir tâches assignées | ✅ | ✅ | ✅ |
| Voir TOUTES les tâches | ❌ | ✅ | ✅ |
| Modifier ses tâches | ✅ | ✅ (ses tâches) | ✅ |
| Modifier TOUTES les tâches | ❌ | ❌ | ✅ |
| Changer statut (ses/assignées) | ✅ | ✅ | ✅ |
| Assigner une tâche | ❌ | ✅ | ✅ |
| Supprimer ses tâches | ✅ | ✅ (ses tâches) | ✅ |
| Supprimer TOUTES les tâches | ❌ | ❌ | ✅ |
| Accès console H2 | ❌ | ❌ | ✅ |

### Implémentation dans TaskManager

Le RBAC est implémenté par **vérification programmatique** dans le service métier (pas par annotations `@PreAuthorize`), ce qui permet plus de flexibilité :

```java
// Exemple : recherche avec RBAC
public Page<Task> searchTasks(..., String currentRole) {
    if ("ADMIN".equals(currentRole) || "MANAGER".equals(currentRole)) {
        // Recherche GLOBALE (voient toutes les tâches)
        return persistencePort.searchTasks(criteria, pageable);
    } else {
        // Recherche LIMITÉE : tâches créées + tâches assignées
        Page<Task> ownedTasks = search(userId: currentUsername, ...);
        Page<Task> assignedTasks = search(assigneeId: currentUsername, ...);
        return merge(ownedTasks, assignedTasks);
    }
}

// Exemple : suppression avec RBAC
public boolean deleteTask(String taskId, String currentRole) {
    if ("ADMIN".equals(currentRole)) {
        // ADMIN peut supprimer n'importe quelle tâche
    } else {
        // USER ne peut supprimer que ses propres tâches
        if (findByIdAndUserId(taskId, currentUsername).isEmpty()) return false;
    }
    softDelete(taskId);
    return true;
}
```

### Pourquoi le RBAC dans le Service et pas le Controller ?

1. **Architecture hexagonale** : Le contrôleur ne doit PAS contenir de logique métier.
2. **Testabilité** : On peut tester le RBAC en mockant le port de persistance.
3. **Réutilisabilité** : Si on ajoute un autre adaptateur (GraphQL, gRPC), le RBAC est automatiquement appliqué.

---

## 6. JPA / Hibernate — Persistence

### Cycle de Vie d'une Entité JPA

```
┌──────────────┐    EntityManager.persist()    ┌──────────────┐
│  TRANSIENT   │ ──────────────────────────→  │   MANAGED    │
│  (nouvel)    │                              │  (suivi par   │
│              │                              │   Hibernate)  │
│  new Task()  │                              │              │
│  Pas d'ID    │                              │  A un ID      │
│  Pas suivi   │                              │  Dirty check  │
└──────────────┘                              └──────┬───────┘
                                                    │
                                            em.detach() ou
                                            Session fermée
                                                    │
                                             ┌──────▼───────┐
                                             │  DETACHED    │
                                             │  (détaché)   │
                                             │              │
                                             │  A un ID      │
                                             │  Pas suivi    │
                                             │  merge() pour│
                                             │  rattacher    │
                                             └──────────────┘
```

### Entity ↔ Domain Mapping

```
┌──────────────────────────┐      ┌──────────────────────────┐
│  DOMAIN (Task record)    │      │  ENTITY (TaskEntity)     │
│  ─────────────────────   │      │  ─────────────────────   │
│  Immutable               │  ↔   │  Mutable (setters)       │
│  Pas d'annotations JPA   │      │  @Entity, @Table, @Id    │
│  toDomain()              │      │  new TaskEntity(task)    │
│  Pas de isNew            │      │  Persistable.isNew()     │
│  Domain pur              │      │  Infrastructure JPA     │
└──────────────────────────┘      └──────────────────────────┘
```

---

## 7. Hibernate Session, L1 Cache & Dirty Checking

### Le Bug NonUniqueObjectException (corrigé)

```
PROBLÈME :
┌───────────────────────────────────────────────────────────────────┐
│ Em.find(TaskEntity, id) → retourne e1 (MANAGED, dans L1 cache)   │
│                                                                     │
│ new TaskEntity(task) → crée e2 (TRANSIENT, isNew=true)            │
│                                                                     │
│ em.persist(e2) → HIBERNATE DÉTECTE :                                  │
│   "Un objet avec l'ID 'xxx' existe déjà dans le Session !"          │
│   → NonUniqueObjectException                                       │
│                                                                     │
│ Pourquoi ?                                                           │
│ Le L1 cache (Level 1) est un Map<ID, Entity> par Session.           │
│ On ne peut pas avoir DEUX objets avec le même ID dans la même       │
│ Session Hibernate.                                                   │
└───────────────────────────────────────────────────────────────────┘

SOLUTION (Dual Path dans TaskPersistenceAdapter.save()) :
┌───────────────────────────────────────────────────────────────────┐
│                                                                     │
│ if (taskRepository.findById(id).isPresent()) {                     │
│     // CHEMIN UPDATE : récupérer l'entité MANAGED et modifier      │
│     TaskEntity managed = existing.get();                           │
│     managed.setTitle(task.title());  // Dirty checking !           │
│     managed.setStatus(task.status());                              │
│     // Hibernate détecte les changements → génère UPDATE auto     │
│ } else {                                                           │
│     // CHEMIN INSERT : nouvelle entité avec isNew=true             │
│     TaskEntity entity = new TaskEntity(task);  // isNew=true      │
│     taskRepository.save(entity);  // → INSERT                      │
│ }                                                                  │
│                                                                     │
│ Interface Persistable<String> :                                     │
│ - isNew() = true  → JPA fait un INSERT                            │
│ - isNew() = false → JPA fait un UPDATE (ou dirty checking)        │
│ - @PostPersist/@PostLoad → markNotNew() (passe isNew à false)     │
└───────────────────────────────────────────────────────────────────┘
```

### Dirty Checking — Comment ça marche ?

```
1. Hibernate charge l'entité depuis la BDD
   → Prend un SNAPSHOT (copie) de tous les champs

2. L'application modifie l'entité via un setter
   → managed.setTitle("Nouveau titre")
   → L'entité en mémoire est modifiée

3. Au flush (avant le commit de la transaction) :
   → Hibernate compare le SNAPSHOT avec l'état actuel
   → "Le champ 'title' a changé de 'Ancien' à 'Nouveau'"
   → Génère automatiquement : UPDATE tasks SET title = 'Nouveau' WHERE id = ?
```

---

## 8. Soft Delete vs Hard Delete

```
❌ HARD DELETE :
DELETE FROM tasks WHERE id = 'xxx'
→ Données perdues DÉFINITIVEMENT
→ Impossible de récupérer
→ Problèmes d'audit et RGPD

✅ SOFT DELETE (utilisé dans TaskSphere) :
UPDATE tasks SET deleted_at = NOW() WHERE id = 'xxx'
→ Tâche archivée mais pas supprimée
→ Récupération possible (deleted_at → null)
→ Audit trail (qui a supprimé, quand)
→ Toutes les requêtes filtrent : WHERE deleted_at IS NULL
```

### Implémentation

```java
// Domain
public Task softDelete() {
    return new Task(..., LocalDateTime.now(), ...);
}

// Repository — TOUTES les requêtes filtrent le soft delete
Optional<TaskEntity> findByIdAndDeletedAtIsNull(String id);
Page<TaskEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);
```

---

## 9. Recherche Dynamique avec JPQL

### Le Problème

Avec N filtres optionnels, on aurait besoin de 2^N méthodes (explosion combinatoire) :

```
8 filtres → 256 méthodes findByXxxAndYyyAndZzz... → IMPOSSIBLE
```

### La Solution : @Query avec "IS NULL OR"

```sql
SELECT t FROM TaskEntity t
WHERE t.deletedAt IS NULL
  AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
  AND (:status IS NULL OR t.status = :status)
  AND (:priority IS NULL OR t.priority = :priority)
  AND (:dueDateFrom IS NULL OR t.dueDate >= :dueDateFrom)
  AND (:dueDateTo IS NULL OR t.dueDate <= :dueDateTo)
  AND (:createdFrom IS NULL OR t.createdAt >= :createdFrom)
  AND (:createdTo IS NULL OR t.createdAt <= :createdTo)
  AND (:userId IS NULL OR t.userId = :userId)
  AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId)
```

**Comment ça marche :**
- Si `:keyword` est `NULL` → `NULL IS NULL` = `TRUE` → le filtre est ignoré
- Si `:keyword` = `"urgence"` → `NULL IS NULL` = `FALSE` → on évalue le `OR` → filtre actif
- Hibernate génère dynamiquement le SQL optimal

### Tri Dynamique

Le tri est passé via `Pageable` (Spring Data) :
```java
Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
```

---

## 10. Pattern Repository avec Spring Data JPA

### Convention de Nommage (Query Derivation)

Spring Data JPA **génère automatiquement** l'implémentation SQL à partir du nom de la méthode :

| Méthode | SQL Généré |
|---------|-----------|
| `findByEmail(String email)` | `SELECT * FROM iam_users WHERE email = ?` |
| `existsByEmail(String email)` | `SELECT COUNT(*) > 0 FROM iam_users WHERE email = ?` |
| `existsByUsername(String username)` | `SELECT COUNT(*) > 0 FROM iam_users WHERE username = ?` |
| `findByUserIdAndRevokedFalse(String id)` | `SELECT * FROM refresh_tokens WHERE user_id = ? AND revoked = false` |
| `findByRevokedFalse()` | `SELECT * FROM refresh_tokens WHERE revoked = false` |
| `findByDeletedAtIsNull()` | `SELECT * FROM tasks WHERE deleted_at IS NULL` |
| `findByIdAndDeletedAtIsNull(String id)` | `SELECT * FROM tasks WHERE id = ? AND deleted_at IS NULL` |

**Convention :**
- `findBy` → `SELECT ... WHERE`
- `And` → `AND`
- `Or` → `OR`
- `False` → `= false`
- `IsNull` → `IS NULL`
- `OrderBy...Desc` → `ORDER BY ... DESC`

---

## 11. Domain-Driven Design — Agregat & Immuabilité

### Pourquoi un Java Record pour le Domain ?

```java
public record Task(String id, String title, ...) {
    // Record = immuable, compact, auto-equals/hashCode/toString
}
```

| Avantage | Explication |
|----------|-------------|
| **Immuabilité** | Pas de setters → pas d'effets de bord |
| **Thread-safe** | Pas besoin de synchronized |
| **Testabilité** | `task.update("x", "y")` retourne un nouvel objet → facile à vérifier |
| **Transparence référentielle** | `f(x) = y`, pas `f(x) modifie x` |

### Factory Methods

```java
// Pas de constructeur public, mais des méthodes statiques nommées
public static Task create(String title, String description, String userId) { ... }
public static Task createWithAssignee(String title, String description, String userId, String assigneeId) { ... }
```

### Wither Pattern (modification immutable)

```java
// Chaque modification retourne une NOUVELLE instance
public Task update(String title, String description) {
    return new Task(this.id, title, description, this.status, ...);
}

// Chaînage possible
task.updatePriority(HIGH).updateDueDate(date).assignTo("user@email.com");
```

---

## 12. Lombok — Réduction du Code Boilerplate

### Annotations utilisées dans TaskSphere

| Annotation | Génère | Utilisée dans |
|-----------|--------|---------------|
| `@Getter` | Tous les getters | UserEntity, RefreshTokenEntity |
| `@Setter` | Tous les setters | UserEntity, RefreshTokenEntity |
| `@NoArgsConstructor` | Constructeur vide | UserEntity, RefreshTokenEntity |
| `@AllArgsConstructor` | Constructeur avec tous les champs | UserEntity, RefreshTokenEntity |
| `@Builder` | Pattern Builder | RefreshTokenEntity |
| `@RequiredArgsConstructor` | Constructeur avec champs `final` | TaskManager, AuthController, etc. |
| `@Slf4j` | `private static final Logger log = ...` | Tous les services |
| `@Service`, `@Component`, `@Repository` | Bean Spring | Services, adaptateurs |

### Piège : @Getter sans @Setter

```java
// ❌ ERREUR qui a causé le bug de compilation !
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity { ... }

// → Les setters n'existent PAS !
// → newUser.setUsername("xxx") → "cannot find symbol: method setUsername"

// ✅ CORRECTION : Ajouter @Setter
@Getter
@Setter  // ← AJOUTÉ
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity { ... }
```

---

## 13. Gestion des Tokens — Dual Token Architecture

### Pourquoi 2 tokens ?

| Token | Type | Durée | Usage | Stockage |
|-------|------|-------|-------|----------|
| **Access Token** | JWT (auto-suffisant) | 1 heure | Authentifier chaque requête | Client (localStorage) |
| **Refresh Token** | UUID opaque (hashé) | 7 jours | Obtenir un nouveau access token | Serveur (BDD, hashé) |

### Refresh Token Rotation

```
1. Client envoie refresh token
2. Serveur vérifie (BCrypt matches sur TOUS les tokens actifs)
3. Serveur RÉVOQUE l'ancien token
4. Serveur crée un NOUVEAU refresh token
5. Serveur génère un NOUVEAU access token
6. Client reçoit les 2 nouveaux tokens

→ Si un refresh token est volé, il ne peut être utilisé qu'UNE fois.
→ Le légitime propriétaire se rendra compte que son token ne fonctionne plus.
```

### Pourquoi hacher le refresh token ?

Même principe que les mots de passe : si la BDD est compromise, l'attaquant ne peut pas réutiliser les tokens volés (il faudrait casser le hash BCrypt).

---

## 14. Assignation de Tâches

### Modèle de Données (Option A)

```
TaskEntity
├── userId: String        ← CRÉATEUR (celui qui a créé la tâche)
├── assigneeId: String?   ← ASSIGNÉ À (optionnel, null si non assignée)
└── ...
```

### Permissions

- **Seuls MANAGER et ADMIN** peuvent assigner/désassigner une tâche (`PATCH /tasks/{id}/assign`)
- **USER** voit les tâches qui lui sont assignées dans la recherche
- **OWNER + ASSIGNEE** peuvent changer le statut de la tâche
- **ADMIN** peut tout faire

---

## 15. Configuration & Environnement

### Variables d'Environnement

| Variable | Usage | Défaut |
|----------|-------|--------|
| `JWT_SECRET` | Clé secrète pour signer les JWT | `tasksphere-dev-secret-key...` |
| `INIT_USER_PASSWORD` | Mot de passe des utilisateurs de test | `password123` |

### Pour la Production

```bash
export JWT_SECRET=votre-cle-secrete-tres-longue-256-bits-minimum-pour-hs256
export INIT_USER_PASSWORD=VotreMotDePasseProduction123
java -jar tasksphere-core.jar
```

### Configuration CORS

Le frontend (localhost:3000) doit pouvoir appeler le backend (localhost:8080). Spring Security configure CORS :

```java
configuration.setAllowedOrigins(List.of("http://localhost:3000"));
// → En production : remplacer par le domaine réel
```

---

## 16. Console H2 — Administration Base de Données

### Accès

| Paramètre | Valeur |
|-----------|-------|
| **URL** | `http://localhost:8080/h2-console` |
| **JDBC URL** | `jdbc:h2:mem:taskspheredb` |
| **Driver** | `org.h2.Driver` |
| **Username** | `sa` |
| **Password** | *(vide)* |
| **Accès** | Réservé au rôle ADMIN |

### Tables

| Table | Description |
|-------|-------------|
| `TASKS` | Tâches (incluant soft-deleted) |
| `IAM_USERS` | Utilisateurs et rôles |
| `REFRESH_TOKENS` | Tokens de rafraîchissement (hashés) |

### Requêtes utiles

```sql
-- Voir toutes les tâches actives
SELECT * FROM tasks WHERE deleted_at IS NULL;

-- Voir les utilisateurs
SELECT id, username, email, role, enabled FROM iam_users;

-- Voir les tâches assignées
SELECT * FROM tasks WHERE assignee_id IS NOT NULL AND deleted_at IS NULL;

-- Compter les tâches par statut
SELECT status, COUNT(*) FROM tasks WHERE deleted_at IS NULL GROUP BY status;
```

---

## 17. Diagramme de Flux Complet

### Flux de Création d'une Tâche avec Assignation

```
Client (MANAGER)              TaskController         TaskManager          TaskPersistenceAdapter
     │                              │                       │                        │
     │ POST /tasks                  │                       │                        │
     │ {title, description,         │                       │                        │
     │  assigneeId: "user@x.com"}  │                       │                        │
     │─────────────────────────────→│                       │                        │
     │                              │ createTask(...)        │                        │
     │                              │──────────────────────→│                        │
     │                              │                       │ getUserInfo(username)   │
     │                              │                       │──────────→ IamUserAdapter
     │                              │                       │←─────────┘
     │                              │                       │                        │
     │                              │                       │ Task.createWithAssignee()
     │                              │                       │                        │
     │                              │                       │ persistencePort.save()  │
     │                              │                       │───────────────────────→│
     │                              │                       │                        │
     │                              │                       │    [DUAL PATH]          │
     │                              │                       │    exists? → setters    │
     │                              │                       │    new? → INSERT       │
     │                              │                       │←───────────────────────│
     │                              │                       │                        │
     │                              │                       │ publishEvent(created)  │
     │                              │                       │──→ Spring Event Pub.    │
     │                              │                       │                        │
     │                              │←──────────────────────│                        │
     │ 201 Created                  │                       │                        │
     │ {task with assigneeId}      │                       │                        │
     │←─────────────────────────────│                       │                        │
```

### Flux de Recherche avec RBAC

```
Client (USER)                 TaskController              TaskManager              Repository
     │                            │                            │                        │
     │ GET /tasks?status=TODO     │                            │                        │
     │───────────────────────────→│                            │                        │
     │                            │ searchTasks(              │                        │
     │                            │   role="USER")             │                        │
     │                            │──────────────────────────→│                        │
     │                            │                            │                        │
     │                            │  USER → Double requête : │                        │
     │                            │  1. ownedTasks (userId)  │──── search(userId=user)──→│
     │                            │  2. assignedTasks        │←─── Page<Task>         │
     │                            │     (assigneeId)        │                        │
     │                            │                            │──── search(assigneeId=──→│
     │                            │                            │←─── Page<Task>         │
     │                            │                            │                        │
     │                            │  MANAGER/ADMIN →         │                        │
     │                            │  Recherche GLOBALE       │──── search(all filters)→│
     │                            │                            │←─── Page<Task>         │
     │                            │                            │                        │
     │                            │←──────────────────────────│                        │
     │ 200 OK                     │                            │                        │
     │ {content, totalPages, ...} │                            │                        │
     │←───────────────────────────│                            │                        │
```

---

*Document généré pour TaskSphere-Platform v0.0.1-SNAPSHOT — Sprint 2*
*Architecture : Spring Boot 3.5 + Architecture Hexagonale + JPA/Hibernate + JWT + RBAC*
