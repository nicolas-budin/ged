package ch.louhan.ged.api.folder;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Un dossier frère porte déjà ce nom → 409. */
@ResponseStatus(HttpStatus.CONFLICT)
class FolderNameAlreadyUsedException extends RuntimeException {

    FolderNameAlreadyUsedException(String name) {
        super("Un dossier nommé '" + name + "' existe déjà à cet endroit");
    }
}
