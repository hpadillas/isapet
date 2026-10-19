package com.isapet.exception;

public class BonoNotFoundException extends RuntimeException {

    public BonoNotFoundException(Long bonoId) {
        super("No se encontró el bono con id " + bonoId + ".");
    }
}
