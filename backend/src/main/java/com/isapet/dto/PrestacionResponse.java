package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrestacionResponse(
        Long id,
        LocalDate fecha,
        Long mascotaId,
        String mascotaNombre,
        Long servicioId,
        String servicioNombre,
        String tipoServicio,
        String prestador,
        String observaciones,
        Long bonoId,
        String bonoCodigo,
        BigDecimal valorPagadoBono
) {
}
