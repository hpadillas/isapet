package com.isapet.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import com.isapet.validation.Patrones;
import jakarta.validation.constraints.Size;

public record CrearAfiliadoRequest(
        @NotBlank(message = "El número de identificación es obligatorio.")
        @Pattern(regexp = Patrones.SOLO_NUMEROS, message = "El documento solo puede contener números.")
        @Size(max = 30, message = "El número de identificación no puede superar los 30 caracteres.")
        String numeroIdentificacion,

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
        String direccion,

        /** Autorización previa, expresa e informada del titular (Ley 1581 de 2012, art. 9). */
        @NotNull(message = "Debes registrar la autorización de tratamiento de datos personales del titular.")
        @AssertTrue(message = "Sin la autorización del titular para el tratamiento de sus datos personales no se puede registrar.")
        Boolean autorizaTratamientoDatos
) {
}