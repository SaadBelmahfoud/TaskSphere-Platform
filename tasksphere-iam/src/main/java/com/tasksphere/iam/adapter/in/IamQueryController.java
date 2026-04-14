package com.tasksphere.iam.adapter.in;

import com.tasksphere.iam.dto.IamUserDto;
import org.springframework.web.bind.annotation.*;

/*
 * @RestController : Expose une API REST interne.
 * @RequestMapping("/api/v1/iam") : Définit la route de ce contexte.
 */
@RestController
@RequestMapping("/api/v1/iam")
public class IamQueryController {

    /*
     * Endpoint pour que le Core puisse demander "Qui est cet utilisateur ?"
     * On met ce endpoint en permitAll dans SecurityConfig pour éviter une boucle infinie
     * (sinon le filtre JWT bloque cet appel, le Core plante, etc.)
     */
    @GetMapping("/users/{username}")
    public IamUserDto getUserInfo(@PathVariable String username) {
        // Simule une récupération en base de données
        return new IamUserDto(username, "ROLE_USER");
    }
}