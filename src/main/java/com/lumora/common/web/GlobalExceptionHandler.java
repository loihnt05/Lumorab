package com.lumora.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(HttpServletRequest request) {
    return error(
        HttpStatus.CONFLICT,
        "CONFLICT",
        "The change conflicts with existing data.",
        Map.of(),
        request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> invalidParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST,
        "BAD_REQUEST",
        "A request parameter has an invalid format.",
        Map.of(exception.getName(), "Invalid format."),
        request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> forbidden(AccessDeniedException exception, HttpServletRequest request) {
    return error(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You do not have permission to access this resource.",
        Map.of(),
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> malformedBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return error(
        HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request body is invalid.", Map.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> details = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Please check the submitted fields.",
        details,
        request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> constraint(
      ConstraintViolationException exception, HttpServletRequest request) {
    Map<String, String> details = new LinkedHashMap<>();
    exception
        .getConstraintViolations()
        .forEach(
            violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage()));
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Please check the submitted fields.",
        details,
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
    if (exception instanceof ErrorResponse response) {
      HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
      if (status != null && status.is4xxClientError()) {
        String code = status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : status.name();
        return error(status, code, status.getReasonPhrase(), Map.of(), request);
      }
      if (exception instanceof ResponseStatusException
          && status == HttpStatus.SERVICE_UNAVAILABLE) {
        return error(
            status,
            "SERVICE_UNAVAILABLE",
            "The service is temporarily unavailable.",
            Map.of(),
            request);
      }
    }
    log.error("Unhandled request failure", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred.",
        Map.of(),
        request);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status,
      String code,
      String message,
      Map<String, String> details,
      HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                code, message, details, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)));
  }
}
