package com.logistock.common;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Transforme les erreurs en réponses JSON { "message": "..." } lisibles par le frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .orElse("Données invalides");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> corpsIllisible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError("Corps de la requête invalide"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> statut(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accesRefuse(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("Accès refusé : votre rôle n'autorise pas cette action"));
    }

    /**
     * Erreurs de la base : les règles métier codées en SQL (stock insuffisant, historique intangible...)
     * remontent ici sous forme de messages clairs.
     */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> baseDeDonnees(DataAccessException ex) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
        String etat = (cause instanceof SQLException sql) ? sql.getSQLState() : null;
        if ("23505".equals(etat)) {
            return conflit("Cette valeur existe déjà (référence, e-mail ou nom déjà utilisé)");
        }
        if ("23503".equals(etat)) {
            return conflit("Opération impossible : cet élément est lié à d'autres données");
        }
        if ("23514".equals(etat) || "P0001".equals(etat)) {
            return conflit(nettoyer(cause.getMessage()));
        }
        LOG.error("Erreur base de données", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Erreur interne du serveur"));
    }

    private static ResponseEntity<ApiError> conflit(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(message));
    }

    /** Garde la première ligne du message PostgreSQL, sans le préfixe "ERROR:". */
    private static String nettoyer(String message) {
        if (message == null) {
            return "Opération refusée par la base de données";
        }
        String ligne = message.split("\n")[0].trim();
        return ligne.startsWith("ERROR:") ? ligne.substring(6).trim() : ligne;
    }
}
