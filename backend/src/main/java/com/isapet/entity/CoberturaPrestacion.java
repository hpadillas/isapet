package com.isapet.entity;

public enum CoberturaPrestacion {
    /** Una de las atenciones mensuales incluidas en el plan; no tiene costo adicional. */
    PLAN,
    /** Atención adicional pagada con un bono, comprado al agotar las incluidas. */
    BONO,
    /** Sin plan ni bono aplicable: se paga a valor pleno. */
    PARTICULAR
}
