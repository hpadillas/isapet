package com.isapet.entity;

public enum EstadoCuota {
    PENDIENTE,
    PAGADA,
    /** Cuota que no se cobra: el afiliado canceló dentro del plazo de pago sin haberla pagado. */
    ANULADA
}
