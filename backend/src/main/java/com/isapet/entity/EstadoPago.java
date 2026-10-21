package com.isapet.entity;

public enum EstadoPago {
    /** Iniciado en la pasarela, a la espera de su confirmación. */
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    ANULADO
}
