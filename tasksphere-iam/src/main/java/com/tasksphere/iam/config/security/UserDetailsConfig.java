package com.tasksphere.iam.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/*
 * @Configuration : Classe de configuration Spring.
 * EN DÉPLAÇANT CETTE MÉTHODE ICI, ON COUPE LE CERCLE VICIEUX.
 * SecurityConfig n'est plus responsable de créer les utilisateurs.
 */
@Configuration
public class UserDetailsConfig {

    /*
     * Ce Bean est maintenant créé INDÉPENDAMMENT de SecurityConfig.
     * Le filtre pourra le récupérer sans déclencher la création de SecurityConfig.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        var user = User.withUsername("saadoune")
                .password("{noop}password123") // {noop} = mot de passe en clair pour la démo
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}