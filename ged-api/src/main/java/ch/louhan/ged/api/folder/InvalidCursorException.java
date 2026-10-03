package ch.louhan.ged.api.folder;

import org.springframework.http.HttpStatus;

import ch.louhan.ged.api.error.GedException;

/** Le curseur fourni par le client n'a pas pu être décodé → 400. */
class InvalidCursorException extends GedException {

    InvalidCursorException() {
        super(HttpStatus.BAD_REQUEST, "invalid-cursor", "Curseur de pagination invalide");
    }
}
