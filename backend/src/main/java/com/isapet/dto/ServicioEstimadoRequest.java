package com.isapet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ServicioEstimadoRequest(
        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId,

        @NotNull(message = "La cantidad mensual es obligatoria.")
        @Min(value = 1, message = "La cantidad mensual debe ser al menos 1.")
        @Max(value = 30, message = "La cantidad mensual no puede superar 30.")
        Integer cantidadMensual
) {
}
