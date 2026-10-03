package ch.louhan.ged.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configuration web commune à toute l'API. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    /**
     * Préfixe {@code /api/v1} ajouté à toutes les routes de nos controllers.
     * Les interfaces générées depuis le contrat déclarent des chemins relatifs ({@code /folders}) :
     * le préfixe est l'URL du serveur indiquée dans le contrat ({@code servers: - url: /api/v1}).
     * Les endpoints techniques (/actuator, Swagger UI) ne sont pas concernés.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forBasePackage("ch.louhan.ged.api"));
    }

    /** Publie le contrat lui-même : GET /api/openapi/ged-v1.yaml (lu par Swagger UI). */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/openapi/**").addResourceLocations("classpath:/openapi/");
    }
}
