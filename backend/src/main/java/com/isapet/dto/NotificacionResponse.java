package com.isapet.dto;

import java.time.OffsetDateTime;

public record NotificacionResponse(
        Long id,
        String tipo,
        String destinatario,
        String asunto,
        String estado,
        String error,
        OffsetDateTime creadaEn
) {
}
