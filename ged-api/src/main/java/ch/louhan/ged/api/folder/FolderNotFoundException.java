package ch.louhan.ged.api.folder;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Le dossier demandé n'existe pas → 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
class FolderNotFoundException extends RuntimeException {

    FolderNotFoundException(UUID id) {
        super("Dossier introuvable : " + id);
    }
}
