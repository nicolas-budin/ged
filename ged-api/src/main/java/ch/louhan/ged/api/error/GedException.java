package ch.louhan.ged.api.error;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier de la GED. Chaque sous-classe fixe un statut HTTP et un {@code code} stable
 * (ex. {@code folder-not-found}) que les applications clientes peuvent utiliser sans dépendre
 * du texte du message. {@link ApiExceptionHandler} la convertit au format RFC 9457.
 */
public abstract class GedException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected GedException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
