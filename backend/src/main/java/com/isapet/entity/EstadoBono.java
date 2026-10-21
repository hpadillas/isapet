package com.isapet.entity;

public enum EstadoBono {
    /** Comprado en línea; se puede usar cuando la pasarela confirme el pago. */
    PENDIENTE_PAGO,
    DISPONIBLE,
    USADO,
    /** El pago en línea fue rechazado: el bono no se puede usar. */
    ANULADO
}
