package com.isapet.dto;

import java.math.BigDecimal;

public record BeneficioServicioResponse(
        Long servicioId,
        String codigo,
        String nombre,
        String tipo,
        BigDecimal valorBase,
        int porcentajeDescuento,
        BigDecimal valorConPlan
) {
}
