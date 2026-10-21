package com.isapet.dto;

import java.math.BigDecimal;

/** Sesión de pago abierta en la pasarela: a dónde ir para pagar. */
public record PagoEnLineaResponse(
        String referencia,
        String urlPago,
        BigDecimal monto,
        String descripcion
) {
}
