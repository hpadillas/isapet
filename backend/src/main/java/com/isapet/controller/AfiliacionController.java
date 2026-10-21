package com.isapet.controller;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.BonoResponse;
import com.isapet.dto.CancelacionPlanResponse;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CotizacionBonoResponse;
import com.isapet.dto.CuotaPlanResponse;
import com.isapet.dto.HistorialPagosResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.PrestacionResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.CancelacionPlanService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.PrestacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final CuotaPlanService cuotaService;
    private final CancelacionPlanService cancelacionService;

    public AfiliacionController(
            AfiliacionService afiliacionService,
            BonoService bonoService,
            PrestacionService prestacionService,
            CuotaPlanService cuotaService,
            CancelacionPlanService cancelacionService) {
        this.afiliacionService = afiliacionService;
        this.bonoService = bonoService;
        this.prestacionService = prestacionService;
        this.cuotaService = cuotaService;
        this.cancelacionService = cancelacionService;
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

    @Operation(
            summary = "Asignar plan al afiliado",
            description = "Asigna el primer plan (se activa hoy), actualiza las mascotas del plan actual (de inmediato) "
                    + "o programa el cambio a otro plan para el día siguiente al final del periodo actual.")
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

    @Operation(
            summary = "Cancelar el cambio de plan programado",
            description = "El afiliado conserva su plan actual; no afecta las mascotas cubiertas hoy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cambio cancelado"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente o sin plan"),
            @ApiResponse(responseCode = "422", description = "No hay un cambio programado")
    })
    @DeleteMapping("/plan/cambio-programado")
    public PlanAfiliadoResponse cancelarCambioProgramado(@PathVariable Long afiliadoId) {
        return afiliacionService.cancelarCambioProgramado(afiliadoId);
    }

    @Operation(
            summary = "Simular la cancelación del plan",
            description = "Indica si el plan terminaría hoy o al final del periodo, y si se anularía la cuota pendiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado que tendría la cancelación"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente o sin plan"),
            @ApiResponse(responseCode = "422", description = "Ya tiene una cancelación programada")
    })
    @GetMapping("/plan/cancelacion")
    public CancelacionPlanResponse simularCancelacion(@PathVariable Long afiliadoId) {
        return cancelacionService.simular(afiliadoId);
    }

    @Operation(
            summary = "Cancelar el plan",
            description = "Con el periodo pagado, el plan sigue hasta su último día. Dentro del plazo de pago sin pagar, "
                    + "termina hoy y la cuota se anula. Si está suspendido, termina hoy y la deuda se conserva.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan cancelado o cancelación programada"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente o sin plan"),
            @ApiResponse(responseCode = "422", description = "Ya tiene una cancelación programada")
    })
    @PostMapping("/plan/cancelacion")
    public CancelacionPlanResponse cancelarPlan(@PathVariable Long afiliadoId) {
        return cancelacionService.cancelar(afiliadoId);
    }

    @Operation(summary = "Deshacer la cancelación programada del plan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelación deshecha; el plan sigue normalmente"),
            @ApiResponse(responseCode = "404", description = "Afiliado inexistente o sin plan"),
            @ApiResponse(responseCode = "422", description = "No hay una cancelación programada")
    })
    @DeleteMapping("/plan/cancelacion")
    public PlanAfiliadoResponse deshacerCancelacion(@PathVariable Long afiliadoId) {
        cancelacionService.deshacer(afiliadoId);
        return afiliacionService.obtenerPlan(afiliadoId);
    }

    @Operation(summary = "Historial de pagos del plan", description = "Cuotas mensuales por periodo, pagadas y pendientes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuotas del afiliado, más recientes primero"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @GetMapping("/pagos")
    public HistorialPagosResponse historialPagos(@PathVariable Long afiliadoId) {
        return cuotaService.historial(afiliadoId);
    }

    @Operation(summary = "Registrar el pago de una cuota")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago registrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado o cuota no encontrados"),
            @ApiResponse(responseCode = "422", description = "La cuota ya estaba pagada")
    })
    @PostMapping("/pagos/{cuotaId}")
    public CuotaPlanResponse registrarPago(
            @PathVariable Long afiliadoId,
            @PathVariable Long cuotaId,
            @Valid @RequestBody RegistrarPagoRequest request) {
        return cuotaService.registrarPago(afiliadoId, cuotaId, request);
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
