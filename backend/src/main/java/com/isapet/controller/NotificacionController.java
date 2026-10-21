package com.isapet.controller;

import com.isapet.dto.NotificacionResponse;
import com.isapet.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Historial de notificaciones enviadas y ejecución manual del recordatorio de fin de periodo. */
@RestController
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final Clock clock;

    public NotificacionController(NotificacionService notificacionService, Clock clock) {
        this.notificacionService = notificacionService;
        this.clock = clock;
    }

    @Operation(summary = "Listar notificaciones del afiliado", description = "Correos enviados o fallidos, más recientes primero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificaciones del afiliado"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente")
    })
    @GetMapping("/api/v1/afiliados/{afiliadoId}/notificaciones")
    public List<NotificacionResponse> listar(@PathVariable Long afiliadoId) {
        return notificacionService.listar(afiliadoId);
    }

    @Operation(
            summary = "Ejecutar recordatorio de fin de periodo",
            description = "Hace lo mismo que la tarea diaria. 'fecha' permite simular otro día (por defecto, hoy).")
    @PostMapping("/api/v1/admin/notificaciones/recordatorio-fin-periodo")
    public Map<String, Integer> recordarFinDePeriodo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate dia = fecha == null ? LocalDate.now(clock) : fecha;
        return Map.of("generados", notificacionService.recordarFinDePeriodo(dia));
    }
}
