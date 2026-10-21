package com.isapet.dto;

/** Situación del plan de un afiliado, para el listado y sus filtros. */
public enum EstadoPlanAfiliado {
    SIN_PLAN,
    /** Plan asignado; se activa al pagar la primera cuota. */
    POR_ACTIVAR,
    ACTIVO,
    /** Cuota pendiente con el plazo de pago vencido. */
    SUSPENDIDO
}
