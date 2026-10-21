package com.isapet.service;

import java.time.LocalDate;

/**
 * Hechos del negocio que generan una notificación al afiliado. Se publican dentro de la transacción
 * y se atienden después del commit, para no notificar cambios que terminen revirtiéndose.
 */
public sealed interface EventoAfiliado {

    Long afiliadoId();

    record AfiliadoRegistrado(Long afiliadoId) implements EventoAfiliado {
    }

    record PlanActivado(Long afiliadoId) implements EventoAfiliado {
    }

    record AtencionesIncluidasAgotadas(Long afiliadoId, LocalDate periodoDesde) implements EventoAfiliado {
    }

    /** {@code ultimoDia}: hasta cuándo conserva el plan (hoy, si la cancelación es inmediata). */
    record PlanCancelado(Long afiliadoId, String plan, LocalDate ultimoDia, boolean inmediata) implements EventoAfiliado {
    }

    record PlanReactivado(Long afiliadoId) implements EventoAfiliado {
    }
}
