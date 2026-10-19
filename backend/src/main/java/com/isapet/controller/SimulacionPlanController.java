package com.isapet.controller;

import com.isapet.dto.ServicioVeterinarioResponse;
import com.isapet.dto.SimulacionPlanRequest;
import com.isapet.dto.SimulacionPlanResponse;
import com.isapet.service.SimulacionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F-03: endpoints públicos para que un interesado compare planes antes de afiliarse. */
@RestController
@RequestMapping("/api/v1/simulaciones")
public class SimulacionPlanController {

    private final SimulacionPlanService simulacionService;

    public SimulacionPlanController(SimulacionPlanService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @Operation(summary = "Listar servicios veterinarios", description = "Servicios disponibles para estimar el uso mensual.")
    @ApiResponse(responseCode = "200", description = "Servicios activos")
    @GetMapping("/servicios")
    public List<ServicioVeterinarioResponse> listarServicios() {
        return simulacionService.listarServicios();
    }

    @Operation(
            summary = "Simular planes",
            description = "Compara los planes activos con condiciones vigentes según la cantidad de mascotas y los servicios esperados al mes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la simulación"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Servicio no encontrado")
    })
    @PostMapping("/planes")
    public SimulacionPlanResponse simular(@Valid @RequestBody SimulacionPlanRequest request) {
        return simulacionService.simular(request);
    }
}
