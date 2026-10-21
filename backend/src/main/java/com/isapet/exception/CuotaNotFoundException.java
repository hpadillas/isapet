package com.isapet.exception;

public class CuotaNotFoundException extends RuntimeException {

    public CuotaNotFoundException(Long cuotaId) {
        super("No se encontró la cuota con id " + cuotaId + ".");
    }
}
