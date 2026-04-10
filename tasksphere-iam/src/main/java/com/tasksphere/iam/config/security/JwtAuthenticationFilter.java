package com.tasksphere.iam.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * DESIGN PATTERN : Chain of Responsibility
 * @Component : C'est un composant technique de filtre.
 * OncePerRequestFilter : Garantie que ce filtre s'exécute UNE SEULE FOIS par requête HTTP (très important pour les performances).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    // UserDetailsService est une interface Spring standard qui permet de charger un utilisateur.
    // Pour la V5, on utilisera une implémentation basique de Spring.
    private final UserDetailsService userDetailsService;

    /*
     * C'est la méthode CŒUR du filtre. Elle intercepte CHAQUE requête.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain // Représente le "fil suivant" dans la chaîne
    ) throws ServletException, IOException {

        log.debug("FILTRE SECURITE : Interception de la requête {}", request.getRequestURI());

        // 1. Extraire l'en-tête "Authorization" de la requête HTTP
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // 2. Vérifications de sécurité : L'en-tête doit exister ET commencer par "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("FILTRE SECURITE : Pas de token Bearer trouvé, on passe au fil suivant");
            filterChain.doFilter(request, response); // On passe au fil suivant SANS identifier l'utilisateur
            return;
        }

        // 3. Extraction du token (on enlève le mot "Bearer ")
        jwt = authHeader.substring(7);
        username = jwtService.extractUsername(jwt);

        // 4. Si on a un username ET que Spring ne connaît pas encore l'utilisateur pour cette requête
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // On simule le chargement de l'utilisateur depuis la BDD (pourra être amélioré en V6)
            var userDetails = this.userDetailsService.loadUserByUsername(username);

            // 5. Cryptographie : On vérifie si le token est valide pour cet utilisateur
            if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                log.info("FILTRE SECURITE : Token valide pour l'utilisateur {}", username);

                /*
                 * CRÉATION DU TICKET D'ENTRÉE.
                 * UsernamePasswordAuthenticationToken est l'objet que Spring Security comprend.
                 * On lui donne l'utilisateur, les droits (vide pour l'instant), et les détails de la requête.
                 */
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                /*
                 * LA MAGIE DE SPRING SECURITY :
                 * On place notre ticket d'entrée dans le "SecurityContextHolder".
                 * C'est un objet global (ThreadLocal) attaché à la requête actuelle.
                 * À partir de maintenant, partout dans le code (Contrôleur, Service), on pourra
                 * demander "Qui est connecté ?" en regardant ce Context.
                 */
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 6. On passe obligatoirement au fil suivant (sinon la requête meurt ici)
        filterChain.doFilter(request, response);
    }
}