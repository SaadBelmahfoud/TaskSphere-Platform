package com.tasksphere.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ═══════════════════════════════════════════════════════════════════
 * CONFIGURATION ASYNCHRONE — ThreadPoolTaskExecutor custom
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 1 — CORRECTION P1-9 : Configuration d'un pool de threads custom
 *
 * PROBLÈME :
 *   Par défaut, Spring Boot utilise SimpleAsyncTaskExecutor :
 *   - Crée un NOUVEAU thread pour chaque tâche @Async
 *   - PAS de limite sur le nombre de threads
 *   - PAS de file d'attente (queue)
 *   → En production : explosion de threads → OutOfMemoryError
 *   → Pas de backpressure : les tâches s'accumulent sans contrôle
 *
 * SOLUTION :
 *   Configurer un ThreadPoolTaskExecutor avec des limites :
 *   - Core pool size : threads toujours actifs (prêts à travailler)
 *   - Max pool size : nombre maximum de threads (limite la mémoire)
 *   - Queue capacity : file d'attente pour les tâches en surplus
 *   - Rejected policy : comportement quand tout est plein
 *
 * PRINCIPE DU POOL DE THREADS :
 * ┌──────────────────────────────────────────────────┐
 * │ ThreadPoolTaskExecutor                           │
 * │                                                  │
 * │  Core Threads [█ █ █ █] ← Toujours actifs       │
 * │  Queue [□□□□□□□□□□]  ← Tâches en attente       │
 * │  Extra Threads [█ █] ← Créés si queue pleine    │
 * │                                                  │
 * │  Si tout est plein → CallerRunsPolicy           │
 * │  (le thread appelant exécute la tâche lui-même) │
 * └──────────────────────────────────────────────────┘
 *
 * RAPPEL — CYCLE DE VIE D'UNE TÂCHE :
 * 1. Nouvelle tâche → Core thread libre ? → OUI → Exécuter
 * 2. Core threads occupés → Queue pleine ? → NON → Enqueue
 * 3. Queue pleine → Max threads atteints ? → NON → Nouveau thread
 * 4. Max threads atteints → RejectedExecutionHandler
 *
 * CHOIX DES VALEURS :
 * - Core = 2 : minimum de threads pour les événements asynchrones
 * - Max = 5 : limite raisonnable pour une application de gestion de tâches
 * - Queue = 50 : permet d'absorber les pics sans créer trop de threads
 * - CallerRunsPolicy : le thread HTTP exécute la tâche → backpressure naturelle
 *
 * POURQUOI CallerRunsPolicy ?
 * - AbortPolicy : lance une exception → la création de tâche échoue
 * - DiscardPolicy : ignore silencieusement → perte de données
 * - DiscardOldestPolicy : ignore la plus ancienne → perte de données
 * - CallerRunsPolicy : le thread appelant fait le travail → ralentit
 *   l'entrée (backpressure) mais ne perd aucune donnée
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bean principal pour l'exécution asynchrone.
     * Utilisé par @Async dans les listeners d'événements.
     *
     * PRINCIPE @Bean :
     * Spring crée une instance de cet Executor et la rend
     * disponible pour l'injection partout dans l'application.
     * L'annotation @EnableAsync indique à Spring d'utiliser
     * ce bean pour les méthodes annotées @Async.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Nombre de threads toujours actifs (même au repos)
        // 2 threads = suffisant pour les listeners asynchrones actuels
        executor.setCorePoolSize(2);

        // Nombre maximum de threads (créés si la queue est pleine)
        // 5 threads = limite pour éviter la consommation excessive
        executor.setMaxPoolSize(5);

        // Capacité de la file d'attente
        // 50 tâches en attente max avant de créer de nouveaux threads
        executor.setQueueCapacity(50);

        // Préfixe des threads (utile dans les logs et JMX)
        // Permet d'identifier facilement les threads asynchrones
        executor.setThreadNamePrefix("tasksphere-async-");

        // Politique de rejet : le thread appelant exécute la tâche
        // CallerRunsPolicy = backpressure naturelle :
        // si le pool est plein, le thread HTTP fait le travail lui-même
        // → ralentit l'entrée mais ne perd aucune tâche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Temps d'inactivité avant fermeture des threads excédentaires
        // 60 secondes = les threads au-dessus du core sont fermés
        // après 60s d'inactivité
        executor.setKeepAliveSeconds(60);

        // Attendre la fin des tâches en cours lors du shutdown
        // true = Spring attend que les tâches se terminent
        // avant de fermer le contexte (graceful shutdown)
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Temps max d'attente pour les tâches en cours lors du shutdown
        // 30 secondes = après 30s, les tâches restantes sont annulées
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        // NOTE : getRejectedExecutionHandler() n'existe pas sur
        // ThreadPoolTaskExecutor dans Spring Framework 6.x.
        // On log la policy directement car on vient de la définir.
        log.info("ThreadPoolTaskExecutor configuré : core={}, max={}, queue={}, rejection=CallerRunsPolicy",
                executor.getCorePoolSize(), executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }
}