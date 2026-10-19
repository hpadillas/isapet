package com.isapet.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpcionPlanSimuladaResponse(
        Long planId,
        String codigo,
        String nombre,
        BigDecimal precioMensual,
        Integer maximoMascotas,
        Integer limiteAtenciones,
        boolean atencionesIlimitadas,
        boolean aplica,
        String motivoNoAplica,
        List<BeneficioServicioResponse> beneficios,
        BigDecimal costoServiciosSinPlan,
        BigDecimal costoServiciosConPlan,
        BigDecimal valorMensualEstimado,
        BigDecimal ahorroMensualEstimado,
        boolean recomendado
) {
}
