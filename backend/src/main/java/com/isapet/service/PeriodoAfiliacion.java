package com.isapet.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Periodo mensual de una afiliación: empieza el día de inicio y dura un mes (afiliado el 20 de octubre,
 * los periodos van del 20 al 19 del mes siguiente). En él se cuentan las atenciones incluidas del plan.
 */
public record PeriodoAfiliacion(LocalDate desde, LocalDate hastaExclusiva) {

    public static PeriodoAfiliacion queContiene(LocalDate fechaInicio, LocalDate fecha) {
        if (fecha.isBefore(fechaInicio)) {
            return new PeriodoAfiliacion(fechaInicio, fechaInicio.plusMonths(1));
        }
        long meses = ChronoUnit.MONTHS.between(fechaInicio, fecha);
        // plusMonths ajusta al último día de los meses cortos (31 de enero + 1 mes = 28 de febrero), así que
        // el periodo siguiente puede empezar antes de que MONTHS.between cuente un mes completo.
        if (!fecha.isBefore(fechaInicio.plusMonths(meses + 1))) {
            meses++;
        }
        return new PeriodoAfiliacion(fechaInicio.plusMonths(meses), fechaInicio.plusMonths(meses + 1));
    }

    /** Último día del periodo. */
    public LocalDate hasta() {
        return hastaExclusiva.minusDays(1);
    }

    public OffsetDateTime inicio(ZoneId zona) {
        return desde.atStartOfDay(zona).toOffsetDateTime();
    }

    public OffsetDateTime fin(ZoneId zona) {
        return hastaExclusiva.atStartOfDay(zona).toOffsetDateTime();
    }
}
