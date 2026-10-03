package ch.louhan.ged.api.folder;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import ch.louhan.ged.api.error.GedException;

/** Le dossier demandé n'existe pas → 404. */
class FolderNotFoundException extends GedException {

    FolderNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "folder-not-found", "Dossier introuvable : " + id);
    }
}
