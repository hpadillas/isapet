package com.isapet.dto;

import com.isapet.entity.MedioPago;
import jakarta.validation.constraints.NotNull;

/** Lo que "elige" quien paga en la página de la pasarela simulada. */
public record ResultadoPagoSimuladoRequest(
        @NotNull Boolean aprobado,
        @NotNull MedioPago medioPago
) {
}
