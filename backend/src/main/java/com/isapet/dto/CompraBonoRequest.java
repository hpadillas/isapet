package com.isapet.dto;

import com.isapet.entity.MedioPago;
import jakarta.validation.constraints.NotNull;

public record CompraBonoRequest(
        @NotNull(message = "La mascota es obligatoria.")
        Long mascotaId,

        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId,

        /** Medio con el que pagó en ISAPET (efectivo, datáfono o transferencia). En línea se usa /bonos/en-linea. */
        @NotNull(message = "El medio de pago es obligatorio.")
        MedioPago medioPago
) {
}
