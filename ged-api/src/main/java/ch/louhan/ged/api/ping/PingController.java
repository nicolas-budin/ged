package ch.louhan.ged.api.ping;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ch.louhan.ged.api.generated.api.PingApi;
import ch.louhan.ged.api.generated.model.PingResponseDto;

/**
 * Endpoint minimal pour vérifier que l'API répond (US-01). La route est déclarée par
 * l'interface {@link PingApi}, générée depuis le contrat {@code openapi/ged-v1.yaml}.
 *
 * <p>Différence avec {@code /actuator/health} : {@code /ping} fait partie de l'API publique
 * versionnée ({@code /api/v1}) et renvoie la version déployée ; le health check est destiné
 * à OpenShift (probes) et vérifie aussi les dépendances (base de données, etc.).
 */
@RestController
public class PingController implements PingApi {

    private final BuildProperties buildProperties;

    public PingController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Override
    public ResponseEntity<PingResponseDto> ping() {
        return ResponseEntity.ok(new PingResponseDto("ok", buildProperties.getVersion()));
    }
}
