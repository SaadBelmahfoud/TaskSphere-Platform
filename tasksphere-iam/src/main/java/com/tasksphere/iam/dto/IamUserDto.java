package com.tasksphere.iam.dto;

/*
 * LE CONTRAT DE L'IAM.
 * Ce DTO appartient au Contexte IAM. Le Core n'a pas le droit de l'importer.
 */
public record IamUserDto(String username, String role) {
}