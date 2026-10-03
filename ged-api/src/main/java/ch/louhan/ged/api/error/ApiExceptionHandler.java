package ch.louhan.ged.api.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convertit toutes les erreurs de l'API au format RFC 9457 ({@code application/problem+json}) :
 * <pre>
 * {"type":"urn:ged:problem:folder-not-found","title":"Not Found","status":404,
 *  "detail":"Dossier introuvable : …","instance":"/api/v1/folders/…","code":"folder-not-found"}
 * </pre>
 *
 * <p>La classe parente {@link ResponseEntityExceptionHandler} traite déjà les erreurs de Spring MVC
 * (JSON illisible, UUID mal formé, méthode HTTP non supportée…). On ajoute ici :
 * nos erreurs métier, le détail champ par champ des erreurs de validation, et un filet de sécurité
 * pour les erreurs imprévues.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Erreurs métier (404, 409, 400…) : statut et code portés par l'exception. */
    @ExceptionHandler(GedException.class)
    ProblemDetail handleGedException(GedException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    /** Corps JSON invalide (ex. nom vide dans CreateFolderRequest) : erreurs par champ. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> fieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Paramètre invalide (ex. limit=500), quand Spring MVC valide lui-même la méthode. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> fieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /**
     * Paramètre invalide, quand la validation passe par {@code @Validated}. C'est le cas des interfaces
     * générées depuis le contrat : sans ce handler, l'erreur remonterait en 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> fieldError(lastSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    /**
     * Filet de sécurité : toute erreur imprévue donne un 500 sans aucun détail technique pour le client.
     * Le détail n'est écrit que dans les logs (sans données personnelles : on ne journalise pas le contenu
     * des requêtes).
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erreur interne");
    }

    /**
     * Point de passage de toutes les erreurs traitées par Spring MVC (UUID mal formé, JSON illisible,
     * méthode non supportée…). On complète leur ProblemDetail avec {@code type} et {@code code},
     * que le contrat déclare obligatoires. Exemple : 400 → code {@code bad-request}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved == null ? "error" : resolved.name().toLowerCase().replace('_', '-');
            problem.setType(URI.create("urn:ged:problem:" + code));
            problem.setProperty("code", code);
        }
        return response;
    }

    // --- Construction des réponses ---

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:ged:problem:" + code));
        problem.setProperty("code", code);
        return problem;
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Données invalides");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    /** « listRootFolders.limit » → « limit » */
    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
