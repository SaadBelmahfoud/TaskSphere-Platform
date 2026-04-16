package com.tasksphere.iam.port.in;

import org.springframework.stereotype.Service;

@Service
public class IamQueryServiceImpl implements IamQueryService {
    @Override
    public String getUserRole(String username) {
        // En vrai, on irait chercher en BDD. Ici, on simule.
        return "ROLE_USER";
    }
}