package ch.louhan.ged.api.folder;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import ch.louhan.ged.api.error.GedException;

/** On ne supprime pas un dossier qui contient encore quelque chose → 409. */
class FolderNotEmptyException extends GedException {

    FolderNotEmptyException(UUID id) {
        super(HttpStatus.CONFLICT, "folder-not-empty", "Le dossier n'est pas vide : " + id);
    }
}
