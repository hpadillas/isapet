package com.isapet.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "La solicitud contiene datos inválidos.",
                errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "La solicitud contiene datos inválidos.",
                List.of("Verifica que la edad sea un número entero y que el cuerpo tenga el formato esperado.")));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequestParameter(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "La solicitud contiene parámetros inválidos o incompletos.",
                List.of()));
    }

    @ExceptionHandler(AfiliadoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAffiliateNotFound(AfiliadoNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(MascotaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePetNotFound(MascotaNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlanNotFound(PlanNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(PlanVersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlanVersionNotFound(PlanVersionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler({
            AfiliacionNotFoundException.class,
            ServicioNotFoundException.class,
            BonoNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleAffiliationResourceNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(DuplicateNumeroIdentificacionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateNumeroIdentificacionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler({
            PlanCodeAlreadyExistsException.class,
            PlanInactiveException.class,
            VersionPlanConflictException.class
    })
    public ResponseEntity<ErrorResponse> handlePlanConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "No fue posible registrar la información porque infringe una restricción de integridad.",
                List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocurrió un error inesperado al procesar la solicitud.",
                List.of()));
    }
}