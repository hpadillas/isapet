package com.isapet.controller;

import com.isapet.service.RenovacionPeriodosJob;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/** Ejecución manual de la renovación diaria de periodos (útil para demostraciones y pruebas). */
@RestController
public class AdminPeriodosController {

    private final RenovacionPeriodosJob renovacion;
    private final Clock clock;

    public AdminPeriodosController(RenovacionPeriodosJob renovacion, Clock clock) {
        this.renovacion = renovacion;
        this.clock = clock;
    }

    @Operation(
            summary = "Renovar periodos",
            description = "Termina los planes cancelados, activa los cambios de plan programados, genera las cuotas "
                    + "y avisa las suspensiones por falta de pago. Hace lo mismo que la tarea de cada madrugada; "
                    + "'fecha' permite simular otro día (por defecto, hoy).")
    @PostMapping("/api/v1/admin/periodos/renovar")
    public Map<String, Integer> renovar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return renovacion.renovar(fecha == null ? LocalDate.now(clock) : fecha);
    }
}
