package com.isapet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SimulacionPlanRequest(
        @NotNull(message = "La cantidad de mascotas es obligatoria.")
        @Min(value = 1, message = "Debe simular al menos una mascota.")
        @Max(value = 20, message = "La cantidad de mascotas no puede superar 20.")
        Integer cantidadMascotas,

        @Size(max = 20, message = "No se pueden simular más de 20 servicios.")
        List<@Valid @NotNull ServicioEstimadoRequest> servicios
) {
    public List<ServicioEstimadoRequest> serviciosOVacio() {
        return servicios == null ? List.of() : servicios;
    }
}
