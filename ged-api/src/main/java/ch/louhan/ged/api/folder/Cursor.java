package ch.louhan.ged.api.folder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Curseur de pagination : la position "après laquelle" reprendre la liste.
 *
 * <p>Pour le client, c'est une chaîne opaque qu'il renvoie telle quelle pour obtenir la page
 * suivante. En interne, c'est le dernier nom de dossier renvoyé, encodé en Base64 (URL-safe)
 * pour pouvoir passer dans une URL et ne pas inciter le client à le fabriquer lui-même.
 */
final class Cursor {

    /** Position de départ : la chaîne vide est inférieure à tout nom de dossier. */
    static final String START = "";

    private Cursor() {
    }

    static String encode(String lastName) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(lastName.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return START;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }
}
