package com.isapet.controller;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.CrearMascotaRequest;
import com.isapet.dto.CrearMascotaResponse;
import com.isapet.dto.ActualizarAfiliadoRequest;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.dto.EstadoPlanAfiliado;
import com.isapet.dto.ResumenEliminacionAfiliadoResponse;
import com.isapet.service.AfiliadoService;
import com.isapet.service.EliminacionAfiliadoService;
import com.isapet.service.MascotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/afiliados")
public class AfiliadoController {

    private final AfiliadoService afiliadoService;
    private final MascotaService mascotaService;
    private final EliminacionAfiliadoService eliminacionService;

    public AfiliadoController(
            AfiliadoService afiliadoService,
            MascotaService mascotaService,
            EliminacionAfiliadoService eliminacionService) {
        this.afiliadoService = afiliadoService;
        this.mascotaService = mascotaService;
        this.eliminacionService = eliminacionService;
    }

    @Operation(
            summary = "Listar afiliados",
            description = "Lista afiliados con paginación, búsqueda, cantidad de mascotas y estado del plan; 'estado' filtra por "
                    + "SIN_PLAN, POR_ACTIVAR, ACTIVO o SUSPENDIDO.")
    @ApiResponse(responseCode = "200", description = "Página de afiliados")
    @GetMapping
    public PagedModel<AfiliadoListaResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoPlanAfiliado estado,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(afiliadoService.listar(q, estado, pageable));
    }

    @Operation(summary = "Consultar afiliado", description = "Obtiene los datos de un afiliado registrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Afiliado encontrado"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @GetMapping("/{afiliadoId}")
    public AfiliadoDetalleResponse obtener(@PathVariable Long afiliadoId) {
        return afiliadoService.obtener(afiliadoId);
    }

    @Operation(summary = "Actualizar afiliado", description = "Actualiza nombres, apellidos y datos de contacto; la identificación no es modificable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Afiliado actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @PutMapping("/{afiliadoId}")
    public AfiliadoDetalleResponse actualizar(
            @PathVariable Long afiliadoId,
            @Valid @RequestBody ActualizarAfiliadoRequest request) {
        return afiliadoService.actualizar(afiliadoId, request);
    }

    @Operation(
            summary = "Registrar afiliado",
            description = "Registra los datos de un afiliado sin crear mascotas automáticamente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Afiliado registrado correctamente",
                    content = @Content(schema = @Schema(implementation = CrearAfiliadoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos"),
            @ApiResponse(responseCode = "409", description = "Número de identificación duplicado"),
            @ApiResponse(responseCode = "500", description = "Error inesperado")
    })
    @PostMapping
    public ResponseEntity<CrearAfiliadoResponse> crear(@Valid @RequestBody CrearAfiliadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(afiliadoService.crear(request));
    }

        @Operation(
            summary = "Agregar mascota a un afiliado",
            description = "Registra una mascota y la asocia al afiliado indicado. Un afiliado puede tener varias mascotas.")
        @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mascota registrada correctamente",
                content = @Content(schema = @Schema(implementation = CrearMascotaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de mascota inválidos"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error inesperado")
        })
        @PostMapping("/{afiliadoId}/mascotas")
        public ResponseEntity<CrearMascotaResponse> crearMascota(
                @PathVariable Long afiliadoId,
            @Valid @RequestBody CrearMascotaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotaService.crear(afiliadoId, request));
        }

    @Operation(summary = "Listar mascotas del afiliado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mascotas del afiliado"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @GetMapping("/{afiliadoId}/mascotas")
    public List<CrearMascotaResponse> listarMascotas(@PathVariable Long afiliadoId) {
        return mascotaService.listar(afiliadoId);
    }

    @Operation(
            summary = "Resumen previo a eliminar el afiliado",
            description = "Cantidad de mascotas, atenciones (historia clínica), bonos y cuotas que se borrarán.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de lo que se eliminará"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @GetMapping("/{afiliadoId}/eliminacion")
    public ResumenEliminacionAfiliadoResponse resumenEliminacion(@PathVariable Long afiliadoId) {
        return eliminacionService.resumen(afiliadoId);
    }

    @Operation(
            summary = "Eliminar afiliado",
            description = "Elimina el afiliado, sus mascotas con su historia clínica, bonos, plan y pagos. No se puede deshacer.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Afiliado eliminado"),
            @ApiResponse(responseCode = "404", description = "Afiliado no encontrado")
    })
    @DeleteMapping("/{afiliadoId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long afiliadoId) {
        eliminacionService.eliminar(afiliadoId);
        return ResponseEntity.noContent().build();
    }

}