package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.config.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 * Point d'entrée public pour demander un Token.
 * EN PRODUCTION, ON NE FERAIT JAMAIS ÇA : Le mot de passe serait vérifié en base de données.
 * Mais pour la V5, c'est le moyen le plus rapide de prouver que le filtre JWT marche.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> generateToken(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        log.info("DEMANDE DE TOKEN pour l'utilisateur : {}", username);

        // En vrai, on vérifierait le mot de passe ici. On skip pour la démo.
        String token = jwtService.generateToken(username);

        log.info("TOKEN GÉNÉRÉ avec succès (tronqué) : {}...", token.substring(0, 20));
        return ResponseEntity.ok(Map.of("token", token));
    }
}