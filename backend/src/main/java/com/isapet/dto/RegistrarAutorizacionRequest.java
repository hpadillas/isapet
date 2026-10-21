package com.isapet.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Registra la autorización de un afiliado que no la tiene (p. ej. registrado antes de este requisito). */
public record RegistrarAutorizacionRequest(
        @NotNull(message = "Debes confirmar la autorización del titular.")
        @AssertTrue(message = "El titular debe autorizar el tratamiento de sus datos personales.")
        Boolean autorizaTratamientoDatos
) {
}
