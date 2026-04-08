package com.tasksphere.core.entity;

import jakarta.persistence.*; // ATTENTION : C'est "jakarta" en Spring Boot 3, plus "javax"

/*
 * @Entity : Dit à Spring "Cette classe représente une table SQL".
 * @Table : Permet de forcer le nom de la table (sinon Hibernate la nommerait "task_entity").
 */
@Entity
@Table(name = "tasks")
public class TaskEntity {

    /*
     * @Id : Clé primaire.
     * Pourquoi on n'utilise pas UUID.randomUUID() comme dans la V1 ?
     * Parce qu'on laisse la BASE DE DONNÉES générer l'ID. C'est la règle d'or en BDD.
     * Ici on utilise une stratégie d'UUID, mais gérée par la BDD.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    // CONSTRUCTEURS : JPA exige un constructeur vide (pour créer l'objet depuis la BDD)
    // et un constructeur avec les champs (pour qu'on puisse créer l'objet depuis le code).
    protected TaskEntity() {}

    public TaskEntity(String title, String description) {
        this.title = title;
        this.description = description;
    }

    // GETTERS : Pas de setters ! On ne modifie pas une entité directement, on la recrée.
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}