package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaPlanResponse(
        Long id,
        String planCodigo,
        String planNombre,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        BigDecimal valor,
        String estado,
        /** Último día para pagar antes de que el plan se suspenda; null en la cuota de activación. */
        LocalDate fechaLimitePago,
        boolean vencida,
        /** Primera cuota: pagarla activa el plan. */
        boolean deActivacion,
        LocalDate fechaPago,
        String medioPago
) {
}
