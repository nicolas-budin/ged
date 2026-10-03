package ch.louhan.ged.api.folder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Le curseur fourni par le client n'a pas pu être décodé → 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class InvalidCursorException extends RuntimeException {

    InvalidCursorException() {
        super("Curseur de pagination invalide");
    }
}
