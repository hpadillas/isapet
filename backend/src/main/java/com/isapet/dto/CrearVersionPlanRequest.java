package com.isapet.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.isapet.validation.StrictIntegerDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearVersionPlanRequest(
        @NotNull(message = "El precio mensual es obligatorio.")
        @DecimalMin(value = "1", message = "El precio mensual debe ser mayor que cero.")
        @Digits(integer = 14, fraction = 0, message = "El precio debe expresarse en pesos colombianos enteros.")
        BigDecimal precioMensual,

        @NotNull(message = "El máximo de mascotas es obligatorio.")
        @Positive(message = "El máximo de mascotas debe ser mayor que cero.")
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        Integer maximoMascotas,

        @Positive(message = "El límite de atenciones debe ser mayor que cero o nulo para ilimitado.")
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        Integer limiteAtenciones,

        @NotNull(message = "La fecha de vigencia es obligatoria.")
        LocalDate vigenteDesde
) {
}
