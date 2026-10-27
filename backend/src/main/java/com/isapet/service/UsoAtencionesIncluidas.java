package com.isapet.service;

/**
 * Uso de las atenciones incluidas del plan en un periodo de la afiliación. {@code limite} es {@code null}
 * cuando el plan incluye atenciones ilimitadas; en ese caso {@code usadas} no se cuenta y vale 0.
 */
public record UsoAtencionesIncluidas(PeriodoAfiliacion periodo, Integer limite, long usadas) {

    public boolean ilimitadas() {
        return limite == null;
    }

    /** Si una atención más todavía la cubre el plan. */
    public boolean quedanDisponibles() {
        return ilimitadas() || usadas < limite;
    }

    /** Atenciones incluidas que quedan en el periodo; solo tiene sentido si el plan tiene límite. */
    public long restantes() {
        if (ilimitadas()) {
            throw new IllegalStateException("El plan incluye atenciones ilimitadas.");
        }
        return limite - usadas;
    }

    /** Si la siguiente atención cubierta por el plan agota las incluidas del periodo. */
    public boolean siguienteEsLaUltima() {
        return !ilimitadas() && usadas + 1 == limite;
    }
}
