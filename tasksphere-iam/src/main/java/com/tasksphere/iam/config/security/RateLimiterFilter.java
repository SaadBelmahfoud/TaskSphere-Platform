package com.tasksphere.iam.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * ═══════════════════════════════════════════════════════════════════
 * FILTRE DE LIMITATION DE DÉBIT (Rate Limiter)
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-10 : Protection brute-force sur /auth/**
 *
 * PROBLÈME :
 *   Les endpoints /auth/login, /auth/register, /auth/refresh sont publics.
 *   Un attaquant peut envoyer des milliers de requêtes par seconde :
 *   - Brute-force de mots de passe sur /auth/login
 *   - Création massive de comptes sur /auth/register
 *   - Refresh token flooding sur /auth/refresh
 *   → Épuisement des ressources serveur (CPU, DB connections)
 *   → Possibilité de découverte de mots de passe
 *
 * SOLUTION :
 *   Un filtre Spring qui limite le nombre de requêtes par IP
 *   sur les endpoints /auth/**. Algorithme : Sliding Window.
 *
 * PRINCIPE DU SLIDING WINDOW :
 * ──────────────────────────────
 * Contrairement au Fixed Window (compteur réinitialisé à chaque minute),
 * le Sliding Window utilise une fenêtre glissante :
 *
 * Fixed Window :  ────|████████|────|████████|──
 *                       minute 1      minute 2
 *                   (5 req/min)   (5 req/min)
 *                   → Un attaquant peut envoyer 10 req en 1 seconde
 *                     à la frontière de deux minutes !
 *
 * Sliding Window : ─────[████████]──────
 *                        fenêtre glissante
 *                        (toujours les 60 dernières secondes)
 *                    → Plus précis, pas de burst à la frontière
 *
 * IMPLÉMENTATION :
 * ──────────────────
 * On utilise un ConcurrentHashMap<IP, Deque<Timestamp>> :
 * - ConcurrentHashMap : thread-safe, accès concurrent sans blocage
 * - Deque : structure double-ended queue pour gérer les timestamps
 * - ConcurrentLinkedDeque : implémentation non-bloquante
 *
 * FLUX DU FILTRE :
 * ┌─────────────────────────────────────────────────────────┐
 * │ 1. La requête est-elle sur /auth/** ?                   │
 * │    → NON → passer au filtre suivant                     │
 * │    → OUI → continuer                                    │
 * │ 2. Extraire l'IP du client                              │
 * │ 3. Récupérer le deque des timestamps pour cette IP      │
 * │ 4. Supprimer les timestamps plus vieux que la fenêtre   │
 * │ 5. Le deque a-t-il plus de MAX_REQUESTS entrées ?       │
 * │    → OUI → 429 Too Many Requests                        │
 * │    → NON → ajouter le timestamp actuel + passer         │
 * └─────────────────────────────────────────────────────────┘
 *
 * LIMITES DE CETTE IMPLEMENTATION :
 * ──────────────────────────────────
 * - In-memory : les compteurs sont perdus au redémarrage
 * - Single-instance : ne fonctionne pas en cluster
 *   (pour un cluster, utiliser Redis avec Bucket4j)
 * - IP-based : plusieurs utilisateurs derrière un même proxy
 *   ont la même IP → limite partagée
 *
 * Pour une production à grande échelle, envisager :
 * - Bucket4j + Redis : rate limiting distribué
 * - CloudFlare / AWS WAF : rate limiting au niveau du CDN
 */
@Slf4j
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    /** Nombre maximum de requêtes autorisées dans la fenêtre */
    private static final int MAX_REQUESTS = 20;

    /** Durée de la fenêtre en millisecondes (1 minute) */
    private static final long WINDOW_SIZE_MS = 60_000;

    /**
     * Map des timestamps de requêtes par adresse IP.
     *
     * ConcurrentHashMap : thread-safe, permet un accès concurrent
     * sans blocage (lock-free reads).
     * ConcurrentLinkedDeque : file doublement chaînée non-bloquante.
     *
     * PRINCIPE : chaque IP a son propre deque de timestamps.
     * On ne partage PAS de limite entre les IPs.
     */
    private final ConcurrentHashMap<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // ═══════════════════════════════════════════════════════
        // Ne limiter que les endpoints /auth/**
        // ═══════════════════════════════════════════════════════
        if (!requestPath.startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIpAddress(request);
        long currentTime = System.currentTimeMillis();

        // Récupérer ou créer le deque pour cette IP
        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(
                clientIp, k -> new ConcurrentLinkedDeque<>());

        // ═══════════════════════════════════════════════════════
        // Sliding Window : supprimer les timestamps hors fenêtre
        // ═══════════════════════════════════════════════════════
        // On retire tous les timestamps plus vieux que WINDOW_SIZE_MS
        // Le deque est trié par ordre d'insertion (chronologique)
        while (!timestamps.isEmpty() && (currentTime - timestamps.peekFirst()) > WINDOW_SIZE_MS) {
            timestamps.pollFirst();
        }

        // ═══════════════════════════════════════════════════════
        // Vérification de la limite
        // ═══════════════════════════════════════════════════════
        if (timestamps.size() >= MAX_REQUESTS) {
            log.warn("RATE LIMIT : IP {} a dépassé la limite de {} req/min sur {}",
                    clientIp, MAX_REQUESTS, requestPath);

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);  // HTTP 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(WINDOW_SIZE_MS / 1000));  // en secondes
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Trop de requêtes. Réessayez dans 60 secondes.\"}"
            );
            return;
        }

        // Ajouter le timestamp de cette requête
        timestamps.addLast(currentTime);

        // Nettoyage périodique : supprimer les IPs inactives
        // (évitons la fuite mémoire avec des IPs qui ne reviennent plus)
        if (requestTimestamps.size() > 1000) {
            cleanupInactiveIps(currentTime);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrait l'adresse IP réelle du client.
     *
     * PRINCIPE : En production, le backend est souvent derrière un
     * reverse proxy (nginx, CloudFlare, AWS ALB). L'IP visible par
     * Java est celle du proxy, pas du client.
     *
     * Headers de proxy standard :
     * - X-Forwarded-For : IP du client (format: client, proxy1, proxy2)
     * - X-Real-IP : IP du client (nginx)
     *
     * SÉCURITÉ : On prend le PREMIER X-Forwarded-For (le client).
     * Les suivants peuvent être spoofés par un attaquant.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Prendre la première IP (le client réel)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        // Fallback : l'IP directe (sans proxy)
        return request.getRemoteAddr();
    }

    /**
     * Nettoyage des IPs inactives pour éviter la fuite mémoire.
     *
     * PRINCIPE : Si un utilisateur se connecte une fois et ne revient
     * plus, son deque reste en mémoire indéfiniment. On nettoie
     * périodiquement les IPs dont tous les timestamps sont expirés.
     */
    private void cleanupInactiveIps(long currentTime) {
        requestTimestamps.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            // Supprimer les timestamps expirés
            while (!timestamps.isEmpty() && (currentTime - timestamps.peekFirst()) > WINDOW_SIZE_MS) {
                timestamps.pollFirst();
            }
            // Si le deque est vide, supprimer l'entrée
            return timestamps.isEmpty();
        });
    }
}