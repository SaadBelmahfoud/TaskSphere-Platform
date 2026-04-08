package com.tasksphere.core.dto;

/*
 * CE FICHIER REPRÉSENTE CE QUE LE CLIENT NOUS ENVOIE.
 * Pourquoi on ne met pas l'ID ici ?
 * Car en architecture REST, c'est le SERVEUR qui décide de l'ID (UUID), jamais le client.
 * Si le client choisit l'ID, il pourrait usurper une tâche existante !
 */
public record TaskCreateRequest(String title, String description) {
}