package com.isapet.exception;

/** La notificación no viene firmada por la pasarela o fue alterada. */
public class FirmaPasarelaInvalidaException extends RuntimeException {

    public FirmaPasarelaInvalidaException() {
        super("La firma de la notificación de la pasarela no es válida.");
    }
}
