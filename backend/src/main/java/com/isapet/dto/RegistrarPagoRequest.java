package com.isapet.dto;

import com.isapet.entity.MedioPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record RegistrarPagoRequest(
        @NotNull(message = "La fecha del pago es obligatoria.")
        @PastOrPresent(message = "La fecha del pago no puede ser futura.")
        LocalDate fechaPago,

        @NotNull(message = "El medio de pago es obligatorio.")
        MedioPago medioPago
) {
}
