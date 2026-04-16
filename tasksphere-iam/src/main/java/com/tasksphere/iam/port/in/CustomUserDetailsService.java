package com.tasksphere.iam.port.in;

import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service d'authentification personnalisé pour Spring Security.
 *
 * Spring Security appelle loadUserByUsername() à chaque requête
 * authentifiée pour vérifier l'utilisateur et récupérer ses droits.
 *
 * NOTE : "Username" dans Spring Security = identifiant de connexion.
 * Dans notre cas, l'identifiant = l'email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Chargement de l'utilisateur par email: {}", email);

        // Rechercher par EMAIL (la méthode s'appelle loadUserByUsername
        // par convention Spring, mais notre identifiant = email)
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur non trouvé avec l'email: " + email));

        // Construire les autorités (rôles)
        // role est un String ("USER", "MANAGER", "ADMIN"), pas un enum
        // On ajoute le préfixe "ROLE_" requis par Spring Security
        // pour que hasRole("USER") fonctionne correctement
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole())
        );

        log.debug("Utilisateur trouvé: {} avec le rôle: {}", email, user.getRole());

        // Retourner l'implémentation UserDetails de Spring Security
        // enabled est un Boolean (wrapper) → Boolean.TRUE.equals() pour un null-safe check
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getEnabled()),
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                true,   // accountNonLocked
                authorities
        );
    }
}