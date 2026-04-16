package com.tasksphere.iam.port.in;

import java.util.Optional;

/**
 * Port d'entrée (inbound port) du module IAM pour les requêtes en lecture.
 *
 * CONCEPT - Architecture Hexagonale - Les Ports :
 * ==============================================
 * Un "port" est une interface Java qui définit un CONTRAT.
 * - Port IN (driving port) : appelé par l'extérieur du domaine (controllers, autres modules)
 * - Port OUT (driven port) : appelé par le domaine vers l'extérieur (base de données, API)
 *
 * Cette interface est un Port IN : le module Core l'utilisera pour interroger
 * le domaine IAM (vérifier un utilisateur, récupérer un rôle, etc.)
 *
 * CONCEPT - Pourquoi des String et pas des UUID ?
 * ==============================================
 * On utilise String pour les IDs car c'est compatible avec le modèle existant
 * du projet (UserEntity utilise String comme type d'ID).
 * Cela évite les conversions UUID ↔ String partout dans le code.
 */
public interface IamQueryService {

    /**
     * Vérifie si un utilisateur existe par son ID.
     * @param userId l'ID de l'utilisateur
     * @return true si l'utilisateur existe en base
     */
    boolean userExistsById(String userId);

    /**
     * Récupère l'ID d'un utilisateur à partir de son email.
     * @param email l'adresse email
     * @return Optional contenant l'ID si trouvé, Optional.empty() sinon
     */
    Optional<String> findUserIdByEmail(String email);

    /**
     * Récupère le rôle d'un utilisateur avec le préfixe "ROLE_".
     * Exemple : retourne "ROLE_USER", "ROLE_MANAGER", "ROLE_ADMIN"
     * @param userId l'ID de l'utilisateur
     * @return Optional contenant le rôle avec préfixe si trouvé
     */
    Optional<String> getUserRole(String userId);
}