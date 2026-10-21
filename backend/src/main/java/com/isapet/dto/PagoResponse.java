package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Transacción del libro de pagos con el origen que la generó (cuota del plan o bono). */
public record PagoResponse(
        Long id,
        String referencia,
        Long afiliadoId,
        String afiliado,
        String concepto,
        /** Origen legible, p. ej. "Cuota plan Básico 20/10/2026 – 19/11/2026" o "Bono BN-000012 · Consulta general · Rocky". */
        String descripcion,
        Long cuotaId,
        Long bonoId,
        BigDecimal monto,
        String medioPago,
        String canal,
        String estado,
        LocalDate fechaPago,
        OffsetDateTime creadoEn,
        OffsetDateTime confirmadoEn,
        String transaccionPasarela,
        String detalle
) {
}
