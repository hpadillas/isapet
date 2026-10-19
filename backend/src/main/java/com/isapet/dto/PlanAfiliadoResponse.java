package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlanAfiliadoResponse(
        Long afiliadoId,
        Long planId,
        String codigo,
        String nombre,
        boolean planActivo,
        LocalDate fechaInicio,
        BigDecimal precioMensual,
        Integer maximoMascotas,
        Integer limiteAtenciones,
        boolean atencionesIlimitadas,
        LocalDate versionVigenteDesde,
        List<MascotaCubiertaResponse> mascotasCubiertas,
        List<BeneficioServicioResponse> beneficios,
        long bonosCompradosMes,
        Integer bonosDisponiblesMes
) {
}
