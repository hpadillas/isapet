package com.isapet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos del panel de inicio del administrador. */
public record ResumenInicioResponse(
        LocalDate fecha,
        long totalAfiliados,
        long afiliadosConPlan,
        long planesActivos,
        long cuotasPendientes,
        BigDecimal valorPendiente,
        long afiliadosSuspendidos,
        long atencionesDelMes,
        /** Situaciones que requieren atención, la más urgente primero. */
        List<Alerta> alertas,
        List<AfiliadoReciente> ultimosAfiliados
) {

    /**
     * @param tipo SUSPENDIDO, PENDIENTE_ACTIVACION, CUOTA_POR_VENCER, CAMBIO_PROGRAMADO o CANCELACION_PROGRAMADA.
     * @param fecha fecha relevante: límite de pago o fecha del cambio o de la cancelación.
     */
    public record Alerta(String tipo, Long afiliadoId, String afiliado, String mensaje, LocalDate fecha) {
    }

    public record AfiliadoReciente(Long id, String nombreCompleto, String numeroIdentificacion, String plan) {
    }
}
