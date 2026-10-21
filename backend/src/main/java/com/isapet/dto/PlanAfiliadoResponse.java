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
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        int atencionesIncluidasUsadas,
        Integer atencionesIncluidasRestantes,
        List<PrestacionResponse> atencionesIncluidasPeriodo,
        List<BonoResponse> bonosPeriodo,
        CambioPlanProgramadoResponse cambioProgramado,
        /** PENDIENTE_ACTIVACION (sin pagar la primera cuota), ACTIVO, o SUSPENDIDO (cuota con el plazo vencido). */
        String estado,
        /** Último día del plan si el afiliado lo canceló; null si no hay cancelación programada. */
        LocalDate fechaFin,
        /** Cuota pendiente más antigua (con su fecha límite de pago), si la hay. */
        CuotaPlanResponse cuotaPendiente
) {
}
