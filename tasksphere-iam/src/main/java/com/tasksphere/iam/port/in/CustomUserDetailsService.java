package com.tasksphere.iam.port.in;

import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/*
 * LE VRAI SERVICE DE SÉCURITÉ.
 * Il implémente l'interface standard de Spring Security.
 * Spring Security appellera automatiquement la méthode loadUserByUsername()
 * quand le JwtAuthenticationFilter demandera l'utilisateur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("IAM DB : Recherche de l'utilisateur '{}' dans la base de données...", username);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé en base : " + username));

        log.info("IAM DB : Utilisateur '{}' trouvé. Vérification du mot de passe par Spring Security...", username);

        // On construit l'objet User de Spring Security à partir de notre Entité.
        // Spring va automatiquement comparer le mot de passe de la requête HTTP avec user.password (le hash BCrypt)
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword()) // Le hash BCrypt stocké en base
                .roles(user.getRole())        // Le rôle (ex: "ROLE_USER")
                .accountLocked(false)
                .credentialsExpired(false)
                .build();
    }
}