package com.isapet.dto;

import java.util.List;

/** Lo que se borrará junto con el afiliado, para mostrarlo antes de confirmar. */
public record ResumenEliminacionAfiliadoResponse(
        Long afiliadoId,
        String numeroIdentificacion,
        String nombreCompleto,
        String plan,
        List<String> mascotas,
        long atenciones,
        long bonos,
        long cuotas
) {
}
