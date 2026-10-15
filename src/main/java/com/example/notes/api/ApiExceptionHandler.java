package com.example.notes.api;

import com.example.notes.service.ForbiddenException;
import com.example.notes.service.NotFoundException;
import com.example.notes.service.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorBody(int status, String error, List<String> details) {
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorBody> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ErrorBody> forbidden(ForbiddenException e) {
        return build(HttpStatus.FORBIDDEN, e.getMessage(), List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ErrorBody> unauthorized(UnauthorizedException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorBody> missingHeader(MissingRequestHeaderException e) {
        return build(HttpStatus.UNAUTHORIZED, "Missing header: " + e.getHeaderName(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> badRequest(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", List.of());
    }

    /** Spring MVC's own errors (unknown route, 405, 415, ...) keep their status; anything else is a 500. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> fallback(Exception e) {
        if (e instanceof ErrorResponse er) {
            int code = er.getStatusCode().value();
            HttpStatus status = HttpStatus.resolve(code);
            String message = status != null ? status.getReasonPhrase() : "Request failed";
            return ResponseEntity.status(code).headers(er.getHeaders())
                    .body(new ErrorBody(code, message, List.of()));
        }
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", List.of());
    }

    private static ResponseEntity<ErrorBody> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ErrorBody(status.value(), message, details));
    }
}
