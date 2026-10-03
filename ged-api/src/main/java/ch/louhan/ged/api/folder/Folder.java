package ch.louhan.ged.api.folder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entité JPA : un objet Java qui correspond à une ligne de la table {@code folder}.
 *
 * <p>Le parent est stocké comme un simple identifiant ({@code parentId}) plutôt que comme
 * une relation JPA vers un autre {@code Folder} : on évite ainsi de charger toute
 * l'arborescence par accident, ce qui compte avec des millions d'éléments.
 */
@Entity
@Table(name = "folder")
public class Folder {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Constructeur vide exigé par JPA ; inutilisable depuis le reste du code. */
    protected Folder() {
    }

    public Folder(UUID parentId, String name) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
