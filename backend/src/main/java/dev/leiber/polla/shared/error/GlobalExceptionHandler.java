package dev.leiber.polla.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 9457 ({@code application/problem+json}) con un formato uniforme.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String PROBLEM_BASE_URI = "urn:polla:problem:";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<Class<? extends DomainException>, HttpStatus> STATUS_BY_EXCEPTION = Map.of(
            NotFoundException.class, HttpStatus.NOT_FOUND,
            ConflictException.class, HttpStatus.CONFLICT,
            UnauthorizedException.class, HttpStatus.UNAUTHORIZED,
            ForbiddenException.class, HttpStatus.FORBIDDEN,
            TooManyRequestsException.class, HttpStatus.TOO_MANY_REQUESTS);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        var status = STATUS_BY_EXCEPTION.getOrDefault(ex.getClass(), HttpStatus.BAD_REQUEST);
        var problem = problem(status, ex.code(), ex.getMessage());
        var response = ResponseEntity.status(status);
        if (ex instanceof TooManyRequestsException tooMany) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(tooMany.retryAfter().toSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "stale-version",
                "El recurso fue modificado por otra persona. Recarga e intenta de nuevo."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "conflict",
                "La operación entra en conflicto con el estado actual. Intenta de nuevo."));
    }

    // Deja que Spring Security responda 401/403 a través de sus propios handlers.
    @ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
    void rethrowSecurity(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Ocurrió un error inesperado."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, "validation", "Los datos enviados no son válidos.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE_URI + code));
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
