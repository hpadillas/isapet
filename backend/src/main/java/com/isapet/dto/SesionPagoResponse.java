package com.isapet.dto;

import java.math.BigDecimal;

/** Datos que muestra la página de pago de la pasarela. */
public record SesionPagoResponse(
        String referencia,
        Long afiliadoId,
        String afiliado,
        String concepto,
        String descripcion,
        BigDecimal monto,
        String estado
) {
}
