package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Pagos de un rango de fechas con sus totales, para conciliar con el banco y la pasarela. */
public record ConciliacionPagosResponse(
        LocalDate desde,
        LocalDate hasta,
        List<PagoResponse> pagos,
        BigDecimal totalAprobado,
        BigDecimal totalPendiente,
        BigDecimal totalRechazado,
        /** Total aprobado por medio de pago (EFECTIVO, TARJETA, TRANSFERENCIA, PSE, NO_REGISTRADO). */
        Map<String, BigDecimal> aprobadoPorMedio
) {
}
