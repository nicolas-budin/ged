package ch.louhan.ged.api.folder;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** On ne supprime pas un dossier qui contient encore quelque chose → 409. */
@ResponseStatus(HttpStatus.CONFLICT)
class FolderNotEmptyException extends RuntimeException {

    FolderNotEmptyException(UUID id) {
        super("Le dossier n'est pas vide : " + id);
    }
}
