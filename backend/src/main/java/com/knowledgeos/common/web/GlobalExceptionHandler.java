package com.knowledgeos.common.web;

import com.knowledgeos.common.exception.NotFoundException;
import com.knowledgeos.common.exception.UnprocessableDocumentException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Formato di errore uniforme (RFC 7807), coerente con 04_API_SPECIFICATION.md §1.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Permessi insufficienti per questa operazione.", request);
    }

    @ExceptionHandler(UnprocessableDocumentException.class)
    public ProblemDetail handleUnprocessable(UnprocessableDocumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "unprocessable-document", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "validation-error", detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Errore interno.", request);
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("https://knowledgeos.dev/errors/" + type));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("traceId", UUID.randomUUID().toString());
        return problemDetail;
    }
}
