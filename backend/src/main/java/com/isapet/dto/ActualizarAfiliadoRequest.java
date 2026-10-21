package com.isapet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.isapet.validation.Patrones;
import jakarta.validation.constraints.Size;

public record ActualizarAfiliadoRequest(
        @NotBlank(message = "Los nombres son obligatorios.")
        @Pattern(regexp = Patrones.NOMBRE_PERSONA, message = "El nombre solo puede contener letras, espacios y el número 6.")
        @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres.")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Pattern(regexp = Patrones.SOLO_PALABRAS, message = "El apellido solo puede contener letras y espacios.")
        @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
        String apellidos,

        @NotBlank(message = "El celular es obligatorio.")
        @Pattern(regexp = Patrones.SOLO_NUMEROS, message = "El celular solo puede contener números.")
        @Size(max = 20, message = "El celular no puede superar los 20 caracteres.")
        String celular,

        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254, message = "El correo electrónico no puede superar los 254 caracteres.")
        String correo,

        @NotBlank(message = "La dirección es obligatoria.")
        @Size(max = 200, message = "La dirección no puede superar los 200 caracteres.")
        String direccion
) {
}