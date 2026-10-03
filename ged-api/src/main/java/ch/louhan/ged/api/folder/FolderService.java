package ch.louhan.ged.api.folder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Règles métier des dossiers. Le controller ne fait que traduire HTTP ⇄ Java ;
 * toutes les décisions (nom unique, dossier vide…) sont prises ici.
 *
 * <p>{@code @Transactional} : chaque méthode publique s'exécute dans une transaction ;
 * en cas d'exception, tout ce qu'elle a écrit en base est annulé.
 */
@Service
@Transactional
public class FolderService {

    private final FolderRepository repository;

    public FolderService(FolderRepository repository) {
        this.repository = repository;
    }

    public Folder create(UUID parentId, String name) {
        if (parentId != null && !repository.existsById(parentId)) {
            throw new FolderNotFoundException(parentId);
        }
        checkNameIsFree(parentId, name);
        return repository.save(new Folder(parentId, name));
    }

    @Transactional(readOnly = true)
    public Folder get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new FolderNotFoundException(id));
    }

    public Folder rename(UUID id, String newName) {
        Folder folder = get(id);
        if (!folder.getName().equals(newName)) {
            checkNameIsFree(folder.getParentId(), newName);
            // Pas besoin d'appeler save() : l'entité est suivie par JPA, la modification
            // est écrite en base automatiquement à la fin de la transaction.
            folder.rename(newName);
        }
        return folder;
    }

    public void delete(UUID id) {
        Folder folder = get(id);
        if (repository.existsByParentId(id)) {
            throw new FolderNotEmptyException(id);
        }
        repository.delete(folder);
    }

    /**
     * Une page de sous-dossiers ({@code parentId} = null pour les dossiers racines).
     * On lit un élément de plus que demandé : s'il existe, il y a une page suivante.
     */
    @Transactional(readOnly = true)
    public Page listChildren(UUID parentId, String cursor, int limit) {
        if (parentId != null && !repository.existsById(parentId)) {
            throw new FolderNotFoundException(parentId);
        }
        String afterName = Cursor.decode(cursor);
        List<Folder> rows = parentId == null
                ? repository.findRoots(afterName, Limit.of(limit + 1))
                : repository.findChildren(parentId, afterName, Limit.of(limit + 1));

        if (rows.size() <= limit) {
            return new Page(rows, null);
        }
        List<Folder> items = rows.subList(0, limit);
        return new Page(items, Cursor.encode(items.getLast().getName()));
    }

    private void checkNameIsFree(UUID parentId, String name) {
        boolean taken = parentId == null
                ? repository.existsByParentIdIsNullAndName(name)
                : repository.existsByParentIdAndName(parentId, name);
        if (taken) {
            throw new FolderNameAlreadyUsedException(name);
        }
    }

    /** Une page de résultats ; {@code nextCursor} vaut null sur la dernière page. */
    public record Page(List<Folder> items, String nextCursor) {
    }
}
