package com.isapet.exception;

/**
 * Solicitud válida en formato que no cumple una regla de negocio (elegibilidad, cobertura, límites).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
