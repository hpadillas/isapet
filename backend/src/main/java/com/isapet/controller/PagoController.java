package com.isapet.controller;

import com.isapet.dto.CompraBonoEnLineaRequest;
import com.isapet.dto.ConciliacionPagosResponse;
import com.isapet.dto.PagoEnLineaResponse;
import com.isapet.dto.PagoResponse;
import com.isapet.entity.CanalPago;
import com.isapet.entity.ConceptoPago;
import com.isapet.entity.EstadoPago;
import com.isapet.entity.MedioPago;
import com.isapet.service.BonoService;
import com.isapet.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Libro de pagos: transacciones del afiliado, pagos en línea y conciliación financiera. */
@RestController
public class PagoController {

    private final PagoService pagoService;
    private final BonoService bonoService;

    public PagoController(PagoService pagoService, BonoService bonoService) {
        this.pagoService = pagoService;
        this.bonoService = bonoService;
    }

    @Operation(summary = "Transacciones del afiliado", description = "Pagos de mensualidades y bonos, con medio, canal y estado.")
    @GetMapping("/api/v1/afiliados/{afiliadoId}/transacciones")
    public List<PagoResponse> transacciones(@PathVariable Long afiliadoId) {
        return pagoService.transaccionesDelAfiliado(afiliadoId);
    }

    @Operation(summary = "Pagar una cuota en línea", description = "Abre la sesión de pago en la pasarela; el pago queda pendiente hasta su confirmación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL de la pasarela para pagar"),
            @ApiResponse(responseCode = "404", description = "Afiliado o cuota no encontrados"),
            @ApiResponse(responseCode = "422", description = "La cuota no está pendiente")
    })
    @PostMapping("/api/v1/afiliados/{afiliadoId}/pagos/{cuotaId}/en-linea")
    public PagoEnLineaResponse pagarCuotaEnLinea(@PathVariable Long afiliadoId, @PathVariable Long cuotaId) {
        return pagoService.iniciarPagoCuota(afiliadoId, cuotaId);
    }

    @Operation(summary = "Comprar un bono pagando en línea", description = "El bono queda pendiente de pago hasta que la pasarela lo confirme.")
    @PostMapping("/api/v1/afiliados/{afiliadoId}/bonos/en-linea")
    public PagoEnLineaResponse comprarBonoEnLinea(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody CompraBonoEnLineaRequest request) {
        return bonoService.comprarEnLinea(afiliadoId, request);
    }

    @Operation(
            summary = "Conciliación de pagos",
            description = "Pagos registrados entre 'desde' y 'hasta' (por defecto, los últimos 30 días) con totales por estado y por medio.")
    @GetMapping("/api/v1/admin/pagos")
    public ConciliacionPagosResponse conciliacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) ConceptoPago concepto,
            @RequestParam(required = false) MedioPago medio,
            @RequestParam(required = false) CanalPago canal) {
        return pagoService.conciliacion(desde, hasta, estado, concepto, medio, canal);
    }
}
