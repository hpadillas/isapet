package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CambioPlanProgramadoResponse(
        Long planId,
        String codigo,
        String nombre,
        LocalDate fechaActivacion,
        BigDecimal precioMensual,
        List<MascotaCubiertaResponse> mascotas
) {
}
