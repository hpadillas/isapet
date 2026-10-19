package com.isapet.dto;

import java.math.BigDecimal;

public record CotizacionBonoResponse(
        Long mascotaId,
        String mascotaNombre,
        Long servicioId,
        String servicioNombre,
        String planCodigo,
        BigDecimal valorBase,
        int porcentajeDescuento,
        BigDecimal valorAPagar,
        BigDecimal ahorro,
        Integer bonosDisponiblesMes
) {
}
