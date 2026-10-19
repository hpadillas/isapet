package com.isapet.dto;

import jakarta.validation.constraints.NotNull;

public record CompraBonoRequest(
        @NotNull(message = "La mascota es obligatoria.")
        Long mascotaId,

        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId
) {
}
