package ch.louhan.ged.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'API de la GED.
 *
 * <p>{@code @SpringBootApplication} active la configuration automatique de Spring Boot
 * et la détection des composants (controllers, services…) dans ce package et ses sous-packages.
 */
@SpringBootApplication
public class GedApplication {

    public static void main(String[] args) {
        SpringApplication.run(GedApplication.class, args);
    }
}
