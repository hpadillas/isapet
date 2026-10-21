package com.isapet.exception;

import java.time.LocalDate;

public class PlanVersionNotFoundException extends RuntimeException {

    public PlanVersionNotFoundException(Long planId, Long versionId) {
        super("El plan " + planId + " no tiene una versión con id " + versionId + ".");
    }

    public PlanVersionNotFoundException(Long planId, LocalDate fecha) {
        super("No hay una versión comercial aplicable para el plan "
                + planId + " en la fecha " + fecha + ".");
    }
}
