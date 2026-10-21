package com.isapet.controller;

import com.isapet.dto.ActualizarPlanRequest;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.ImpactoEliminacionVersionResponse;
import com.isapet.dto.CrearVersionPlanRequest;
import com.isapet.dto.PlanResponse;
import com.isapet.dto.VersionPlanResponse;
import com.isapet.exception.PlanVersionNotFoundException;
import com.isapet.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/planes")
public class PlanAdminController {

    private final PlanService planService;

    public PlanAdminController(PlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "Listar planes", description = "Lista el catálogo y el historial comercial de cada plan.")
    @ApiResponse(responseCode = "200", description = "Catálogo de planes")
    @GetMapping
    public List<PlanResponse> listar() {
        return planService.listarPlanes();
    }

    @Operation(summary = "Consultar plan", description = "Obtiene un plan con todas sus versiones comerciales.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan encontrado"),
            @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @GetMapping("/{planId}")
    public PlanResponse obtener(@PathVariable Long planId) {
        return planService.obtenerPlan(planId);
    }

    @Operation(
            summary = "Consultar versión aplicable",
            description = "Obtiene la versión con la fecha de inicio más reciente que no supera la fecha indicada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versión aplicable encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe plan o versión aplicable")
    })
    @GetMapping("/{planId}/version-aplicable")
    public VersionPlanResponse obtenerVersionAplicable(
            @PathVariable Long planId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return planService.obtenerVersionAplicable(planId, fecha)
                .orElseThrow(() -> new PlanVersionNotFoundException(planId, fecha));
    }

    @Operation(summary = "Crear plan", description = "Crea un plan con su primera versión comercial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plan creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Código de plan duplicado")
    })
    @PostMapping
    public ResponseEntity<PlanResponse> crear(@Valid @RequestBody CrearPlanRequest request) {
        PlanResponse response = planService.crearPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Publicar versión comercial",
            description = "Agrega una versión inmutable con fecha posterior a la última versión y no retroactiva.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versión comercial publicada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Plan no encontrado"),
            @ApiResponse(responseCode = "409", description = "Plan inactivo o fecha de vigencia en conflicto")
    })
    @PostMapping("/{planId}/versiones")
    public PlanResponse publicarVersion(
            @PathVariable Long planId,
            @Valid @RequestBody CrearVersionPlanRequest request) {
        return planService.publicarVersion(planId, request);
    }

    @Operation(
            summary = "Impacto de eliminar una versión programada",
            description = "Cuántos afiliados tienen programado el cambio a este plan dentro de la vigencia de la versión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Impacto de la eliminación"),
            @ApiResponse(responseCode = "404", description = "Plan o versión no encontrados"),
            @ApiResponse(responseCode = "409", description = "La versión ya empezó o es la única del plan")
    })
    @GetMapping("/{planId}/versiones/{versionId}/eliminacion")
    public ImpactoEliminacionVersionResponse impactoEliminacionVersion(
            @PathVariable Long planId,
            @PathVariable Long versionId) {
        return planService.impactoEliminacionVersion(planId, versionId);
    }

    @Operation(
            summary = "Eliminar una versión programada",
            description = "Solo versiones que aún no empiezan. La versión anterior queda vigente hasta el inicio de la siguiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versión eliminada; devuelve el plan con su historial"),
            @ApiResponse(responseCode = "404", description = "Plan o versión no encontrados"),
            @ApiResponse(responseCode = "409", description = "La versión ya empezó o es la única del plan")
    })
    @DeleteMapping("/{planId}/versiones/{versionId}")
    public PlanResponse eliminarVersion(@PathVariable Long planId, @PathVariable Long versionId) {
        return planService.eliminarVersion(planId, versionId);
    }

    @Operation(
            summary = "Actualizar datos del plan",
            description = "Cambia el nombre del plan de inmediato. Para cambiar precio, mascotas o atenciones se publica una versión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @PatchMapping("/{planId}")
    public PlanResponse actualizar(@PathVariable Long planId, @Valid @RequestBody ActualizarPlanRequest request) {
        return planService.actualizarPlan(planId, request);
    }

    @Operation(
            summary = "Desactivar plan",
            description = "Desactiva el plan sin eliminarlo ni modificar su historial comercial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan desactivado"),
            @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @PatchMapping("/{planId}/desactivar")
    public PlanResponse desactivar(@PathVariable Long planId) {
        return planService.desactivarPlan(planId);
    }
}
