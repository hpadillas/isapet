package com.isapet.dto;

public record AfiliadoListaResponse(
        Long id,
        String numeroIdentificacion,
        String nombres,
        String apellidos,
        String celular,
        String correo,
        String direccion,
        long cantidadMascotas,
        /** Nombre del plan, o null si no tiene. */
        String plan,
        /** Ver {@link EstadoPlanAfiliado}. */
        String estadoPlan
) {
}