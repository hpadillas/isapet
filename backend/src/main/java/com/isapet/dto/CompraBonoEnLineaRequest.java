package com.isapet.dto;

import jakarta.validation.constraints.NotNull;

/** Compra de bono pagada en línea: el medio (tarjeta o PSE) se elige en la pasarela. */
public record CompraBonoEnLineaRequest(
        @NotNull(message = "La mascota es obligatoria.")
        Long mascotaId,

        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId
) {
}
