package com.isapet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegistrarPrestacionRequest(
        @NotNull(message = "La mascota es obligatoria.")
        Long mascotaId,

        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId,

        @NotNull(message = "La fecha de la prestación es obligatoria.")
        @PastOrPresent(message = "La fecha de la prestación no puede ser futura.")
        LocalDate fecha,

        @NotBlank(message = "El prestador es obligatorio.")
        @Size(max = 150, message = "El prestador no puede superar los 150 caracteres.")
        String prestador,

        @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
        String observaciones,

        Long bonoId
) {
}
