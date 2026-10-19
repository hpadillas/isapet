package com.isapet.exception;

public class AfiliacionNotFoundException extends RuntimeException {

    public AfiliacionNotFoundException(Long afiliadoId) {
        super("El afiliado con id " + afiliadoId + " no tiene un plan de afiliación asignado.");
    }
}
