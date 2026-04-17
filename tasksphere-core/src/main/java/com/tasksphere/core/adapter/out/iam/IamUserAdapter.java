package com.tasksphere.core.adapter.out.iam;

import com.tasksphere.core.dto.UserInfo;
import com.tasksphere.core.port.out.UserInformationPort;
import com.tasksphere.iam.port.in.IamQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/*
 * L'ADAPTATEUR DE PONT.
 * Il implémente le Port du Core, mais pour le faire, il utilise directement un Bean de IAM.
 * AUCUN APPEL HTTP. Juste un appel de méthode Java : iamQueryService.getUserRoleByEmail()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamUserAdapter implements UserInformationPort {

    // Spring injecte automatiquement le Bean de IAM car il est dans le classpath !
    private final IamQueryService iamQueryService;

    @Override
    public UserInfo getUserInfo(String username) {
        log.debug("ADAPTATEUR IAM : Appel direct en mémoire vers le module IAM pour {}", username);

        // ← CORRIGÉ : avant on appelait getUserRole(username) qui cherche par UUID
        // Or username = email (venant du JWT subject), donc findById(email) ne trouvait rien.
        // Maintenant on appelle getUserRoleByEmail(username) qui cherche par email.
        String role = iamQueryService.getUserRoleByEmail(username).orElse("ROLE_UNKNOWN");
        return new UserInfo(username, role);
    }
}