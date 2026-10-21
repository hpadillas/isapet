package com.isapet.dto;

import com.isapet.validation.Patrones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos del plan que no forman parte de sus condiciones comerciales (no generan una versión nueva). */
public record ActualizarPlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio.")
        @Pattern(regexp = Patrones.SOLO_PALABRAS, message = "El nombre del plan solo puede contener letras y espacios.")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
        String nombre
) {
}
