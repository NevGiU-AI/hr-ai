package com.nevgiu.hrai.web;

import com.nevgiu.hrai.candidate.ingestion.CvIngestionException;
import com.nevgiu.hrai.evaluation.EvaluationException;
import com.nevgiu.hrai.security.AccountAdministrationException;
import com.nevgiu.hrai.security.PasswordManagementException;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final SecurityAuditService audit;

    public ApiExceptionHandler(SecurityAuditService audit) {
        this.audit = audit;
    }

    @ExceptionHandler(AccountAdministrationException.class)
    public ResponseEntity<ApiError> handleAccountAdministration(
            AccountAdministrationException exception, HttpServletRequest request) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        AppUserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal value
                ? value : null;
        audit.administrationDenied(principal, request.getRequestURI(), exception.getStatus().value());
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(CvIngestionException.class)
    public ResponseEntity<ApiError> handleIngestion(CvIngestionException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(EvaluationException.class)
    public ResponseEntity<ApiError> handleEvaluation(EvaluationException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(PasswordManagementException.class)
    public ResponseEntity<ApiError> handlePasswordManagement(
            PasswordManagementException exception, HttpServletRequest request) {
        return response(exception.status(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadLimit(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the configured request size limit", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), validationErrors));
    }
}
