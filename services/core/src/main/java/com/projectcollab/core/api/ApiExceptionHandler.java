package com.projectcollab.core.api;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String,Object>> denied(AccessDeniedException e) {
        return response(HttpStatus.FORBIDDEN, "forbidden", e.getMessage());
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String,Object>> missing(NoSuchElementException e) {
        return response(HttpStatus.NOT_FOUND, "not_found", "Requested resource was not found");
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,Object>> invalid(Exception e) {
        return response(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }
    private ResponseEntity<Map<String,Object>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message == null ? code : message));
    }
}

