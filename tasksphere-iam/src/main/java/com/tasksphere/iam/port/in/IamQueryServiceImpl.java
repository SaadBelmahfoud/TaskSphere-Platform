package com.tasksphere.iam.port.in;

import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implémentation du port d'entrée IamQueryService.
 *
 * CONCEPT - Port d'entrée (Inbound Port) en architecture hexagonale :
 * ==================================================================
 * Cette classe implémente l'interface IamQueryService qui définit
 * les opérations de LECTURE sur le domaine IAM.
 *
 * Le module Core utilisera cette interface pour :
 * - Vérifier qu'un utilisateur existe avant de lui lier une tâche
 * - Récupérer le rôle d'un utilisateur pour les vérifications d'autorisation
 *
 * CONCEPT - @Service + injection par constructeur :
 * ================================================
 * @Service = Spring crée un bean singleton de cette classe au démarrage.
 * @RequiredArgsConstructor = Lombok génère un constructeur avec tous les champs
 *   `final`. Spring injecte automatiquement les dépendances via ce constructeur.
 *   C'est la méthode d'injection RECOMMANDÉE par l'équipe Spring (plutôt que
 *   @Autowired sur un champ, qui rend le code plus difficile à tester).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IamQueryServiceImpl implements IamQueryService {

    /** Repository d'accès aux données utilisateur (port de sortie / outbound port) */
    private final UserRepository userRepository;

    /**
     * Vérifie si un utilisateur existe par son ID.
     *
     * @param userId l'ID de l'utilisateur (String, pas UUID)
     * @return true si l'utilisateur existe en base
     */
    @Override
    public boolean userExistsById(String userId) {
        log.debug("Vérification de l'existence de l'utilisateur: {}", userId);
        return userRepository.findById(userId).isPresent();
    }

    /**
     * Récupère l'ID de l'utilisateur à partir de son email.
     *
     * @param email l'email de l'utilisateur
     * @return Optional contenant l'ID (String) si trouvé, Optional.empty() sinon
     */
    @Override
    public Optional<String> findUserIdByEmail(String email) {
        log.debug("Recherche de l'ID utilisateur par email: {}", email);
        return userRepository.findByEmail(email).map(user -> user.getId());
    }

    /**
     * Récupère le rôle d'un utilisateur.
     *
     * Le rôle est retourné avec le préfixe "ROLE_" requis par Spring Security.
     * Exemple : "USER" en base → "ROLE_USER" retourné.
     *
     * Pourquoi ce préfixe ?
     * Spring Security exige que les rôles commencent par "ROLE_"
     * quand on utilise hasRole() dans les règles de sécurité.
     * Exemple : @PreAuthorize("hasRole('ADMIN')") vérifie "ROLE_ADMIN".
     *
     * @param userId l'ID de l'utilisateur (String)
     * @return Optional contenant le rôle avec préfixe si trouvé
     */
    @Override
    public Optional<String> getUserRole(String userId) {
        log.debug("Récupération du rôle pour l'utilisateur: {}", userId);
        return userRepository.findById(userId)
                .map(user -> "ROLE_" + user.getRole());
    }
}