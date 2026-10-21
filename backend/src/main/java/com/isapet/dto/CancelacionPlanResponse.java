package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Qué implica (o implicó) cancelar el plan del afiliado. */
public record CancelacionPlanResponse(
        String plan,
        /** true si el plan termina hoy; false si sigue hasta el final del periodo pagado. */
        boolean inmediata,
        /** Último día en que el afiliado tiene el plan. */
        LocalDate ultimoDia,
        String motivo,
        /** Valor de la cuota anulada (cancelación dentro del plazo de pago sin haberla pagado); 0 si no hay. */
        BigDecimal valorAnulado,
        /** Cuotas vencidas que siguen pendientes de pago después de cancelar. */
        BigDecimal deudaPendiente
) {
}
