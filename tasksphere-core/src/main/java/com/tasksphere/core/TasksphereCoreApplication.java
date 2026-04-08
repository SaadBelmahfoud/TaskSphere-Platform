package com.tasksphere.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * @SpringBootApplication est une "Méta-annotation".
 * C'est équivalent à coller ces 3 annotations au-dessus de la classe :
 *
 * 1. @Configuration : Indique que cette classe est une source de définitions de Beans.
 * 2. @EnableAutoConfiguration : Dit à Spring : "Devine ce dont j'ai besoin en fonction des dépendances Maven".
 *    -> IL A DÉTECTÉ spring-boot-starter-web DANS LE POM.XML !
 *    -> IL S'EST DIT : "AH ! ON FAIT DU WEB ! JE VAIS DÉMARRER UN SERVEUR TOMCAT SUR LE PORT 8080 !"
 * 3. @ComponentScan : "Fouille le package com.tasksphere.core et ses sous-packages".
 *    -> IL A TROUVÉ @Service (TaskManager) -> IL LE CRÉE.
 *    -> IL A TROUVÉ @RestController (TaskController) -> IL L'ENREGISTRE COMME ROUTE WEB.
 */
@SpringBootApplication
public class TasksphereCoreApplication {

    /*
     * Le point d'entrée absolu de Java (public static void main).
     * Quand tu cliques sur "Play" dans IntelliJ, c'est cette ligne qui tourne.
     *
     * SpringApplication.run() fait deux choses gigantesques :
     * 1. Elle crée le Conteneur IoC (crée les Beans, injecte les dépendances).
     * 2. Elle démarre le serveur Tomcat intégré (qui commence à écouter le port 8080).
     */
    public static void main(String[] args) {
        SpringApplication.run(TasksphereCoreApplication.class, args);
    }
}