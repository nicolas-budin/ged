package ch.louhan.ged.api.folder;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ch.louhan.ged.api.generated.api.FoldersApi;
import ch.louhan.ged.api.generated.model.CreateFolderRequestDto;
import ch.louhan.ged.api.generated.model.FolderDto;
import ch.louhan.ged.api.generated.model.FolderPageDto;
import ch.louhan.ged.api.generated.model.RenameFolderRequestDto;

/**
 * Endpoints REST des dossiers (US-02), conformes au contrat {@code openapi/ged-v1.yaml}.
 *
 * <p>Les routes, les paramètres et leur validation sont déclarés dans l'interface {@link FoldersApi},
 * <b>générée depuis le contrat</b> : ce controller ne contient que l'implémentation
 * (appeler le service, convertir entité ⇄ DTO). Il ne contient aucune règle métier.
 */
@RestController
public class FolderController implements FoldersApi {

    private final FolderService service;

    public FolderController(FolderService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<FolderDto> createFolder(CreateFolderRequestDto request) {
        Folder folder = service.create(request.getParentId(), request.getName().strip());
        return ResponseEntity.created(URI.create("/api/v1/folders/" + folder.getId()))
                .body(toDto(folder));
    }

    @Override
    public ResponseEntity<FolderPageDto> listRootFolders(String cursor, Integer limit) {
        return ResponseEntity.ok(toDto(service.listChildren(null, cursor, limit)));
    }

    @Override
    public ResponseEntity<FolderDto> getFolder(UUID id) {
        return ResponseEntity.ok(toDto(service.get(id)));
    }

    @Override
    public ResponseEntity<FolderPageDto> listChildFolders(UUID id, String cursor, Integer limit) {
        return ResponseEntity.ok(toDto(service.listChildren(id, cursor, limit)));
    }

    @Override
    public ResponseEntity<FolderDto> renameFolder(UUID id, RenameFolderRequestDto request) {
        return ResponseEntity.ok(toDto(service.rename(id, request.getName().strip())));
    }

    @Override
    public ResponseEntity<Void> deleteFolder(UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Conversion entité → objets JSON générés ---

    private static FolderDto toDto(Folder folder) {
        return new FolderDto(folder.getId(), folder.getName(), folder.getCreatedAt().atOffset(ZoneOffset.UTC))
                .parentId(folder.getParentId());
    }

    private static FolderPageDto toDto(FolderService.Page page) {
        return new FolderPageDto(page.items().stream().map(FolderController::toDto).toList())
                .nextCursor(page.nextCursor());
    }
}
