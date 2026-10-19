package com.isapet.exception;

public class ServicioNotFoundException extends RuntimeException {

    public ServicioNotFoundException(Long servicioId) {
        super("No se encontró el servicio veterinario con id " + servicioId + ".");
    }
}
