package com.isapet.exception;

public class PagoNotFoundException extends RuntimeException {

    public PagoNotFoundException(String referencia) {
        super("No se encontró el pago con referencia " + referencia + ".");
    }
}
