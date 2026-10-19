package com.isapet.dto;

import java.time.LocalDate;
import java.util.List;

public record SimulacionPlanResponse(
        LocalDate fechaReferencia,
        Integer cantidadMascotas,
        List<OpcionPlanSimuladaResponse> opciones
) {
}
