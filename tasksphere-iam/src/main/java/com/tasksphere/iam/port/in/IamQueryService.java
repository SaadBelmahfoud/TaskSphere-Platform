package com.tasksphere.iam.port.in;

/*
 * Un Port Entrant côté IAM, mais qui sera utilisé comme contrat par le module Core.
 * Pourquoi ? Parce que c'est IAM qui détient la vérité sur les utilisateurs.
 */
public interface IamQueryService {
    String getUserRole(String username);
}