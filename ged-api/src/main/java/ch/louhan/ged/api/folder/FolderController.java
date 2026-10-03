package ch.louhan.ged.api.folder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST des dossiers (US-02). Ce controller ne contient aucune règle métier :
 * il valide l'entrée, appelle {@link FolderService} et convertit le résultat en JSON.
 *
 * <p>Validation : Spring MVC applique lui-même les contraintes ({@code @Min}, {@code @NotBlank}…)
 * sur les paramètres et les corps de requête, et répond 400 si elles ne sont pas respectées.
 * (Ne pas ajouter {@code @Validated} sur la classe : cela activerait un autre mécanisme,
 * dont l'erreur remonterait en 500.)
 */
@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    private final FolderService service;

    public FolderController(FolderService service) {
        this.service = service;
    }

    /** POST /api/v1/folders → 201 Created + en-tête Location vers le nouveau dossier. */
    @PostMapping
    public ResponseEntity<FolderResponse> create(@Valid @RequestBody CreateFolderRequest request) {
        Folder folder = service.create(request.parentId(), request.name().strip());
        return ResponseEntity.created(URI.create("/api/v1/folders/" + folder.getId()))
                .body(FolderResponse.of(folder));
    }

    /** GET /api/v1/folders → les dossiers racines. */
    @GetMapping
    public PageResponse listRoots(@RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return PageResponse.of(service.listChildren(null, cursor, limit));
    }

    @GetMapping("/{id}")
    public FolderResponse get(@PathVariable UUID id) {
        return FolderResponse.of(service.get(id));
    }

    @GetMapping("/{id}/children")
    public PageResponse listChildren(@PathVariable UUID id,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return PageResponse.of(service.listChildren(id, cursor, limit));
    }

    /** PATCH /api/v1/folders/{id} → renommer. */
    @PatchMapping("/{id}")
    public FolderResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameFolderRequest request) {
        return FolderResponse.of(service.rename(id, request.name().strip()));
    }

    /** DELETE /api/v1/folders/{id} → 204 No Content (409 si le dossier n'est pas vide). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Objets échangés en JSON (remplacés à l'itération 3 par ceux générés depuis le contrat OpenAPI) ---

    /** Un nom de dossier : non vide, 255 caractères max, sans '/' ni '\' (réservés aux chemins). */
    private static final String NAME_PATTERN = "[^/\\\\]+";

    public record CreateFolderRequest(
            @NotBlank @Size(max = 255) @Pattern(regexp = NAME_PATTERN) String name,
            UUID parentId) {
    }

    public record RenameFolderRequest(
            @NotBlank @Size(max = 255) @Pattern(regexp = NAME_PATTERN) String name) {
    }

    public record FolderResponse(UUID id, String name, UUID parentId, Instant createdAt) {
        static FolderResponse of(Folder f) {
            return new FolderResponse(f.getId(), f.getName(), f.getParentId(), f.getCreatedAt());
        }
    }

    public record PageResponse(List<FolderResponse> items, String nextCursor) {
        static PageResponse of(FolderService.Page page) {
            return new PageResponse(page.items().stream().map(FolderResponse::of).toList(), page.nextCursor());
        }
    }
}
