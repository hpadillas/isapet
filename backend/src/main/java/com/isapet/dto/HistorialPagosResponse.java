package com.isapet.dto;

import java.math.BigDecimal;
import java.util.List;

public record HistorialPagosResponse(
        BigDecimal totalPagado,
        BigDecimal totalPendiente,
        List<CuotaPlanResponse> cuotas
) {
}
