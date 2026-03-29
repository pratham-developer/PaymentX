package com.pratham.paymentx.advice;

import com.pratham.paymentx.exception.*;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /* @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleRateLimitExceededException(RateLimitExceededException exception){
        log.warn("Rate limit exceeded. Retry after {} seconds.", exception.getRetryAfterSeconds());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .message("Too many requests. Please try again in " + exception.getRetryAfterSeconds() + " seconds.")
                .build();
        return buildErrorResponseEntity(apiError);
    }
     */

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidRoleException(InvalidRoleException exception){
        log.warn("Invalid Role: {}", exception.getMessage());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.FORBIDDEN)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }



    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Payload JSON parsing failed: {}", ex.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Malformed JSON request. Please check your syntax (e.g., trailing commas).")
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingHeaderException(MissingRequestHeaderException ex) {
        log.warn("Missing or malformed request header '{}': {}", ex.getHeaderName(), ex.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(String.format("Required header '%s' is missing or invalid", ex.getHeaderName()))
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        assert ex.getRequiredType() != null;
        String message = String.format("Parameter '%s' has invalid value. Expected type: %s",
                ex.getName(), ex.getRequiredType().getSimpleName());

        log.warn("Method argument type mismatch: {}", message);

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(message)
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationExceptions(MethodArgumentNotValidException exception) {
        String errorMessage = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(", "));

        log.warn("Request body validation failed: {}", errorMessage);

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Validation Failed: " + errorMessage)
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodValidationExceptions(HandlerMethodValidationException exception) {
        log.warn("Handler method validation failed: {}", exception.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Invalid request parameters: " + exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolationException(ConstraintViolationException exception) {
        String errorMessage = exception.getConstraintViolations()
                .stream()
                .map(violation -> {
                    String propertyPath = violation.getPropertyPath().toString();
                    String field = propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
                    return field + ": " + violation.getMessage();
                })
                .distinct()
                .collect(Collectors.joining(", "));

        log.warn("Database/Entity constraint violation: {}", errorMessage);

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Validation Failed: " + errorMessage)
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDeniedException(AccessDeniedException exception){
        log.warn("Access denied for request: {}", exception.getMessage());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.FORBIDDEN)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(AuthenticationException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<?>> handleJwtException(JwtException exception){
        log.warn("JWT validation failed: {}", exception.getMessage());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(ExternalAuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleExternalAuthException(ExternalAuthenticationException exception) {
        // Logging as ERROR with stack trace because this means our downstream provider (Google) is failing or unreachable.
        log.error("External Authentication Gateway Error: ", exception);

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_GATEWAY)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidToken(InvalidTokenException exception) {
        log.warn("Invalid authentication token presented: {}", exception.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(ResourceNotFoundException exception) {
        log.warn("Resource not found: {}", exception.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.NOT_FOUND)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(BadRequestException exception) {
        log.warn("Bad request executed: {}", exception.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(exception.getMessage())
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation (Conflict): {}", ex.getMostSpecificCause().getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.CONFLICT)
                .message("Resource already exists or conflicts with another record.")
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLock(OptimisticLockingFailureException exception) {
        log.warn("Optimistic locking conflict detected: {}", exception.getMessage());
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.CONFLICT)
                .message("The resource was updated by another process. Please refresh and retry.")
                .build();
        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class
    })
    public ResponseEntity<ApiResponse<?>> handlePessimisticLock(Exception exception) {
        log.warn("Concurrent request blocked by database lock: {}", exception.getMessage());

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .message("Request already in progress. Please wait a moment and try again.")
                .build();

        return buildErrorResponseEntity(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleInternalServerError(Exception exception) {
        // Logging as ERROR with full stack trace for untracked 500s
        log.error("Unhandled Exception caught in GlobalExceptionHandler: ", exception);

        ApiError apiError = ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("An unexpected error occurred. Please try again later.") // Sanitized message for user
                .build();
        return buildErrorResponseEntity(apiError);
    }

    private ResponseEntity<ApiResponse<?>> buildErrorResponseEntity(ApiError apiError) {
        return new ResponseEntity<>(new ApiResponse<>(apiError), apiError.getStatus());
    }
}