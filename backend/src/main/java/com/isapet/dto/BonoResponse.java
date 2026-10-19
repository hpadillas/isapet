package com.isapet.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BonoResponse(
        Long id,
        String codigo,
        Long mascotaId,
        String mascotaNombre,
        Long servicioId,
        String servicioNombre,
        String tipoServicio,
        String planCodigo,
        BigDecimal valorBase,
        int porcentajeDescuento,
        BigDecimal valorPagado,
        OffsetDateTime fechaCompra,
        String estado
) {
}
