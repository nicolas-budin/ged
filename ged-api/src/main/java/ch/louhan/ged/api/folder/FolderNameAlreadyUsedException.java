package ch.louhan.ged.api.folder;

import org.springframework.http.HttpStatus;

import ch.louhan.ged.api.error.GedException;

/** Un dossier frère porte déjà ce nom → 409. */
class FolderNameAlreadyUsedException extends GedException {

    FolderNameAlreadyUsedException(String name) {
        super(HttpStatus.CONFLICT, "folder-name-already-used", "Un dossier nommé '" + name + "' existe déjà à cet endroit");
    }
}
