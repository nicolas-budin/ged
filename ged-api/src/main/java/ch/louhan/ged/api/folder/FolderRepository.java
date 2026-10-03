package ch.louhan.ged.api.folder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Accès aux dossiers en base. Spring Data génère l'implémentation au démarrage :
 * <ul>
 *   <li>les méthodes héritées de {@link JpaRepository} (save, findById, delete…) ;</li>
 *   <li>les méthodes dont le <em>nom</em> décrit la requête ({@code existsBy…}) ;</li>
 *   <li>les méthodes annotées {@link Query}, avec la requête écrite explicitement.</li>
 * </ul>
 */
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    boolean existsByParentId(UUID parentId);

    boolean existsByParentIdAndName(UUID parentId, String name);

    boolean existsByParentIdIsNullAndName(String name);

    /*
     * Pagination par curseur ("keyset") : au lieu de "sauter N lignes" (OFFSET, de plus en plus lent),
     * on demande "les dossiers dont le nom vient APRÈS le dernier nom déjà renvoyé".
     * L'index unique (parent_id, name) permet à PostgreSQL d'y aller directement.
     * Le nom suffit comme curseur car il est unique parmi les enfants d'un même parent.
     */

    @Query("""
            select f from Folder f
            where f.parentId = :parentId and f.name > :afterName
            order by f.name""")
    List<Folder> findChildren(UUID parentId, String afterName, Limit limit);

    @Query("""
            select f from Folder f
            where f.parentId is null and f.name > :afterName
            order by f.name""")
    List<Folder> findRoots(String afterName, Limit limit);
}
