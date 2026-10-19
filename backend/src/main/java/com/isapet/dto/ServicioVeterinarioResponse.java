package com.isapet.dto;

import java.math.BigDecimal;

public record ServicioVeterinarioResponse(
        Long id,
        String codigo,
        String nombre,
        String tipo,
        BigDecimal valorBase
) {
}
