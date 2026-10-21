package com.isapet.dto;

import java.time.OffsetDateTime;

public record AutorizacionDatosResponse(
        boolean registrada,
        String versionPolitica,
        OffsetDateTime otorgadaEn,
        String registradaPor,
        String medio
) {
}
