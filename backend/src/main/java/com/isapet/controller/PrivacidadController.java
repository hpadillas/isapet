package com.isapet.controller;

import com.isapet.dto.AutorizacionDatosResponse;
import com.isapet.dto.PoliticaPrivacidadResponse;
import com.isapet.dto.RegistrarAutorizacionRequest;
import com.isapet.service.AutorizacionDatosService;
import com.isapet.service.PoliticaPrivacidad;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Tratamiento de datos personales (Ley 1581 de 2012): aviso de privacidad y prueba de la autorización. */
@RestController
public class PrivacidadController {

    private final PoliticaPrivacidad politica;
    private final AutorizacionDatosService autorizacionService;

    public PrivacidadController(PoliticaPrivacidad politica, AutorizacionDatosService autorizacionService) {
        this.politica = politica;
        this.autorizacionService = autorizacionService;
    }

    @Operation(summary = "Aviso de privacidad y política de tratamiento de datos personales", description = "Público.")
    @GetMapping("/api/v1/privacidad")
    public PoliticaPrivacidadResponse politica() {
        return politica.politica();
    }

    @Operation(summary = "Autorización de tratamiento de datos del afiliado", description = "Prueba de la autorización (art. 17-b).")
    @GetMapping("/api/v1/afiliados/{afiliadoId}/autorizacion-datos")
    public AutorizacionDatosResponse autorizacion(@PathVariable Long afiliadoId) {
        return autorizacionService.consultar(afiliadoId);
    }

    @Operation(
            summary = "Registrar la autorización de un afiliado existente",
            description = "Para afiliados registrados antes de pedir la autorización al crearlos.")
    @PostMapping("/api/v1/afiliados/{afiliadoId}/autorizacion-datos")
    public AutorizacionDatosResponse registrarAutorizacion(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody RegistrarAutorizacionRequest request) {
        return autorizacionService.registrarPosterior(afiliadoId);
    }
}
