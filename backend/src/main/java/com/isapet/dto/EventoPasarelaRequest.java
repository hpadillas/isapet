package com.isapet.dto;

import com.isapet.entity.MedioPago;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Notificación que la pasarela envía al webhook cuando termina un pago. Viene firmada (encabezado
 * X-Firma, HMAC-SHA256) para comprobar que la envía la pasarela y que nadie la alteró.
 */
public record EventoPasarelaRequest(
        @NotBlank String referencia,
        @NotNull @Pattern(regexp = "APROBADO|RECHAZADO") String estado,
        @NotBlank String transaccion,
        @NotNull BigDecimal monto,
        @NotNull MedioPago medioPago,
        String motivo
) {
}
