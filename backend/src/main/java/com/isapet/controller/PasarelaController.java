package com.isapet.controller;

import com.isapet.dto.EventoPasarelaRequest;
import com.isapet.dto.PagoResponse;
import com.isapet.dto.ResultadoPagoSimuladoRequest;
import com.isapet.dto.SesionPagoResponse;
import com.isapet.service.FirmaPasarela;
import com.isapet.service.PagoService;
import com.isapet.service.WebhookPasarelaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

/**
 * Pasarela de pagos: el webhook que recibe las notificaciones y la pasarela simulada de pruebas.
 */
@RestController
public class PasarelaController {

    private final WebhookPasarelaService webhookService;
    private final PagoService pagoService;
    private final FirmaPasarela firma;

    public PasarelaController(WebhookPasarelaService webhookService, PagoService pagoService, FirmaPasarela firma) {
        this.webhookService = webhookService;
        this.pagoService = pagoService;
        this.firma = firma;
    }

    @Operation(
            summary = "Webhook de la pasarela",
            description = "La pasarela notifica aquí el resultado de cada pago. El cuerpo debe venir firmado con "
                    + "HMAC-SHA256 en el encabezado X-Firma. Es idempotente: reenviar la misma notificación no la aplica dos veces.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación aplicada (o ya aplicada antes)"),
            @ApiResponse(responseCode = "401", description = "Firma ausente o inválida"),
            @ApiResponse(responseCode = "404", description = "Referencia de pago desconocida"),
            @ApiResponse(responseCode = "422", description = "El monto no coincide con el del pago")
    })
    @PostMapping("/api/v1/pasarela/webhook")
    public PagoResponse webhook(
            @RequestHeader(name = "X-Firma", required = false) String firmaRecibida,
            @Valid @RequestBody EventoPasarelaRequest evento) {
        return webhookService.procesar(evento, firmaRecibida);
    }

    @Operation(summary = "Pasarela simulada: datos de la sesión de pago")
    @GetMapping("/api/v1/admin/pasarela-simulada/{referencia}")
    public SesionPagoResponse sesion(@PathVariable String referencia) {
        return pagoService.sesion(referencia);
    }

    @Operation(
            summary = "Pasarela simulada: resultado del pago",
            description = "Actúa como la pasarela: arma la notificación, la firma y la entrega al mismo procesamiento del webhook.")
    @PostMapping("/api/v1/admin/pasarela-simulada/{referencia}/resultado")
    public PagoResponse resultado(
            @PathVariable String referencia,
            @Valid @RequestBody ResultadoPagoSimuladoRequest request) {
        SesionPagoResponse sesion = pagoService.sesion(referencia);
        EventoPasarelaRequest evento = new EventoPasarelaRequest(
                referencia,
                request.aprobado() ? "APROBADO" : "RECHAZADO",
                "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                sesion.monto(),
                request.medioPago(),
                request.aprobado() ? null : "Pago rechazado en la pasarela simulada.");
        return webhookService.procesar(evento, firma.firmar(evento));
    }
}
