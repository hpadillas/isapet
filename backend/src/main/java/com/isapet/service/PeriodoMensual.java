package com.isapet.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Mes calendario en la zona del servidor, usado para el límite mensual de atenciones. */
record PeriodoMensual(OffsetDateTime desde, OffsetDateTime hastaExclusiva) {

    static PeriodoMensual actual(Clock clock) {
        ZoneId zona = clock.getZone();
        LocalDate primerDia = LocalDate.now(clock).withDayOfMonth(1);
        return new PeriodoMensual(
                primerDia.atStartOfDay(zona).toOffsetDateTime(),
                primerDia.plusMonths(1).atStartOfDay(zona).toOffsetDateTime());
    }
}
