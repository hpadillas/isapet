package com.isapet.controller;

import com.isapet.dto.ResumenInicioResponse;
import com.isapet.service.ResumenInicioService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumenInicioController {

    private final ResumenInicioService resumenService;

    public ResumenInicioController(ResumenInicioService resumenService) {
        this.resumenService = resumenService;
    }

    @Operation(
            summary = "Resumen para el panel de inicio",
            description = "Cifras generales, situaciones que requieren atención y últimos afiliados registrados.")
    @GetMapping("/api/v1/admin/resumen")
    public ResumenInicioResponse resumen() {
        return resumenService.resumen();
    }
}
