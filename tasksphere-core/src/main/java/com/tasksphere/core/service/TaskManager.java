package com.tasksphere.core.service;

import com.tasksphere.core.domain.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*
 * @Service : Dit à Spring "Crée un Bean de cette classe au démarrage".
 * @Slf4j : Génère automatiquement un logger "log" (Lombok).
 *           En industrie, on n'utilise JAMAIS System.out.println() car ça ne peut pas être filtré.
 */
@Slf4j
@Service
public class TaskManager {

    // Pour la V1, on simule une base de données en mémoire.
    private final List<Task> inMemoryTasks = new ArrayList<>();

    /*
     * DESIGN PATTERN : Constructor Injection (Injection par Constructeur)
     * Pas besoin de mettre @Autowired au-dessus. Depuis Spring 4.3+,
     * si une classe n'a qu'un seul constructeur, Spring injecte automatiquement
     * ce dont il a besoin (ici, rien, mais la règle est établie).
     */
    public TaskManager() {
        // Ce log prouve que c'est SPRING qui instancie la classe, pas nous.
        log.info("🚀 [INIT] Le Bean TaskManager vient d'être créé par le Conteneur Spring IoC.");
    }

    public Task createTask(String title, String description) {
        log.debug("Exécution de la logique métier pour créer : {}", title);
        Task task = Task.create(title, description);
        inMemoryTasks.add(task);
        return task;
    }

    public List<Task> getAllTasks() {
        // List.copyOf renvoie une liste non-modifiable (bonne pratique de sécurité)
        return List.copyOf(inMemoryTasks);
    }
}