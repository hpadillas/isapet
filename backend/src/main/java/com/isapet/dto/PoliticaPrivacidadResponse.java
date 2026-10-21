package com.isapet.dto;

import java.time.LocalDate;
import java.util.List;

/** Aviso de privacidad y política de tratamiento de datos (Ley 1581 de 2012 y Decreto 1074 de 2015). */
public record PoliticaPrivacidadResponse(
        String version,
        LocalDate vigenteDesde,
        String responsable,
        String nit,
        String direccion,
        String correo,
        String telefono,
        List<String> datosRecolectados,
        List<String> finalidades,
        List<String> derechos,
        String canalAtencion,
        int diasHabilesConsultas,
        int diasHabilesReclamos
) {
}
