package com.isapet.dto;

import java.time.LocalDate;

/** Qué pasa si se elimina una versión programada, para mostrarlo antes de confirmar. */
public record ImpactoEliminacionVersionResponse(
        Long versionId,
        LocalDate vigenteDesde,
        /** Afiliados con un cambio programado a este plan que se activaría mientras rige esta versión. */
        long afiliadosConCambioProgramado
) {
}
