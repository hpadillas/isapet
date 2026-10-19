package com.isapet.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AsignarPlanRequest(
        @NotNull(message = "El plan es obligatorio.")
        Long planId,

        @NotEmpty(message = "Selecciona al menos una mascota cubierta.")
        List<@NotNull Long> mascotaIds
) {
}
