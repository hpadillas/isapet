package com.isapet.controller;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.BonoResponse;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CotizacionBonoResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.PrestacionResponse;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.PrestacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F-04, F-05 y F-06: plan, bonos y prestaciones de un afiliado. */
@RestController
@RequestMapping("/api/v1/afiliados/{afiliadoId}")
public class AfiliacionController {

    private final AfiliacionService afiliacionService;
    private final BonoService bonoService;
    private final PrestacionService prestacionService;

    public AfiliacionController(
            AfiliacionService afiliacionService,
            BonoService bonoService,
            PrestacionService prestacionService) {
        this.afiliacionService = afiliacionService;
        this.bonoService = bonoService;
        this.prestacionService = prestacionService;
    }

    @Operation(summary = "Consultar plan del afiliado", description = "Condiciones vigentes, beneficios, mascotas cubiertas y uso del mes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan del afiliado"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente o sin plan asignado")
    })
    @GetMapping("/plan")
    public PlanAfiliadoResponse obtenerPlan(@PathVariable Long afiliadoId) {
        return afiliacionService.obtenerPlan(afiliadoId);
    }

    @Operation(summary = "Asignar plan al afiliado", description = "Asigna o cambia el plan y define las mascotas cubiertas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan asignado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado o plan no encontrado"),
            @ApiResponse(responseCode = "422", description = "Plan inactivo, mascotas ajenas o excede el máximo de mascotas")
    })
    @PutMapping("/plan")
    public PlanAfiliadoResponse asignarPlan(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody AsignarPlanRequest request) {
        return afiliacionService.asignarPlan(afiliadoId, request);
    }

    @Operation(summary = "Listar bonos del afiliado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bonos comprados, más recientes primero"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @GetMapping("/bonos")
    public List<BonoResponse> listarBonos(@PathVariable Long afiliadoId) {
        return bonoService.listar(afiliadoId);
    }

    @Operation(summary = "Cotizar bono", description = "Verifica elegibilidad y calcula el valor del bono sin registrarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotización"),
            @ApiResponse(responseCode = "404", description = "Afiliado, mascota o servicio no encontrado"),
            @ApiResponse(responseCode = "422", description = "El afiliado no es elegible")
    })
    @GetMapping("/bonos/cotizacion")
    public CotizacionBonoResponse cotizarBono(
            @PathVariable Long afiliadoId,
            @RequestParam Long mascotaId,
            @RequestParam Long servicioId) {
        return bonoService.cotizar(afiliadoId, mascotaId, servicioId);
    }

    @Operation(summary = "Comprar bono", description = "Registra la compra de un bono para una mascota cubierta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bono comprado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado, mascota o servicio no encontrado"),
            @ApiResponse(responseCode = "422", description = "El afiliado no es elegible")
    })
    @PostMapping("/bonos")
    public ResponseEntity<BonoResponse> comprarBono(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody CompraBonoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bonoService.comprar(afiliadoId, request));
    }

    @Operation(summary = "Consultar historial de prestaciones", description = "Prestaciones de las mascotas del afiliado, más recientes primero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de prestaciones"),
            @ApiResponse(responseCode = "404", description = "Afiliado o mascota no encontrado")
    })
    @GetMapping("/prestaciones")
    public List<PrestacionResponse> listarPrestaciones(
            @PathVariable Long afiliadoId,
            @RequestParam(required = false) Long mascotaId) {
        return prestacionService.listar(afiliadoId, mascotaId);
    }

    @Operation(summary = "Registrar prestación", description = "Registra una atención realizada; si se indica un bono, lo marca como usado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Prestación registrada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado, mascota, servicio o bono no encontrado"),
            @ApiResponse(responseCode = "422", description = "Bono no utilizable para esta prestación")
    })
    @PostMapping("/prestaciones")
    public ResponseEntity<PrestacionResponse> registrarPrestacion(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody RegistrarPrestacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prestacionService.registrar(afiliadoId, request));
    }
}
