package com.isapet.controller;

import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.CrearMascotaResponse;
import com.isapet.dto.ActualizarAfiliadoRequest;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.DuplicateNumeroIdentificacionException;
import com.isapet.exception.MascotaNotFoundException;
import com.isapet.exception.GlobalExceptionHandler;
import com.isapet.service.AfiliadoService;
import com.isapet.service.EliminacionAfiliadoService;
import com.isapet.service.MascotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La autorización se cubre en la configuración de seguridad; aquí se prueba solo el contrato HTTP.
@WebMvcTest({AfiliadoController.class, MascotaController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AfiliadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AfiliadoService afiliadoService;

    @MockBean
    private MascotaService mascotaService;

    @MockBean
    private EliminacionAfiliadoService eliminacionService;

    @Test
    void registraAfiliadoSinCrearMascota() throws Exception {
        when(afiliadoService.crear(any())).thenReturn(
                new CrearAfiliadoResponse(7L, "100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1"));

        mockMvc.perform(post("/api/v1/afiliados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAffiliateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.celular").value("3001234567"))
                .andExpect(jsonPath("$.correo").value("ana@example.com"))
                .andExpect(jsonPath("$.direccion").value("Calle 1"))
                .andExpect(jsonPath("$.mascota").doesNotExist());
    }

    @Test
    void responde400CuandoFaltanDatosObligatoriosDelAfiliado() throws Exception {
        mockMvc.perform(post("/api/v1/afiliados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

        @Test
        void responde400CuandoElCorreoNoEsValido() throws Exception {
                mockMvc.perform(post("/api/v1/afiliados")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(validAffiliateRequest().replace("ana@example.com", "correo-invalido")))
                                .andExpect(status().isBadRequest());
        }

    @Test
    void responde409CuandoElNumeroDeIdentificacionYaExiste() throws Exception {
        when(afiliadoService.crear(any()))
                .thenThrow(new DuplicateNumeroIdentificacionException("100"));

        mockMvc.perform(post("/api/v1/afiliados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAffiliateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registraMascotaParaElAfiliadoIndicado() throws Exception {
        when(mascotaService.crear(eq(7L), any())).thenReturn(
                new CrearMascotaResponse(12L, 7L, "Rocky", "Perro", "Labrador", 3, "Café"));

        mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.afiliadoId").value(7))
                .andExpect(jsonPath("$.raza").value("Labrador"))
                .andExpect(jsonPath("$.edad").value(3));
    }

    @Test
    void listaAfiliadosConConteoDeMascotasYParametrosDeBusqueda() throws Exception {
        when(afiliadoService.listar(eq("ana"), any(), any())).thenReturn(new PageImpl<>(
                java.util.List.of(new AfiliadoListaResponse(7L, "100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1", 2L,
                        "Básico", "ACTIVO")),
                PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/afiliados?q=ana&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroIdentificacion").value("100"))
                .andExpect(jsonPath("$.content[0].cantidadMascotas").value(2))
                .andExpect(jsonPath("$.content[0].plan").value("Básico"))
                .andExpect(jsonPath("$.content[0].estadoPlan").value("ACTIVO"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void consultaAfiliadoExistente() throws Exception {
        when(afiliadoService.obtener(7L)).thenReturn(affiliateDetails());

        mockMvc.perform(get("/api/v1/afiliados/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.numeroIdentificacion").value("100"));
    }

    @Test
    void responde404AlConsultarAfiliadoInexistente() throws Exception {
        when(afiliadoService.obtener(999L)).thenThrow(new AfiliadoNotFoundException(999L));

        mockMvc.perform(get("/api/v1/afiliados/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizaAfiliadoSinAceptarIdentificacionEnElRequest() throws Exception {
        when(afiliadoService.actualizar(eq(7L), any(ActualizarAfiliadoRequest.class)))
                .thenReturn(new AfiliadoDetalleResponse(7L, "100", "Ana María", "Gomez", "3001234567", "ana@example.com", "Calle 1"));

        mockMvc.perform(put("/api/v1/afiliados/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Ana María"))
                .andExpect(jsonPath("$.numeroIdentificacion").value("100"));
    }

    @Test
    void responde404AlActualizarAfiliadoInexistente() throws Exception {
        when(afiliadoService.actualizar(eq(999L), any(ActualizarAfiliadoRequest.class)))
                .thenThrow(new AfiliadoNotFoundException(999L));

        mockMvc.perform(put("/api/v1/afiliados/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isNotFound());
    }

    @Test
    void obtieneMascotasDeUnAfiliado() throws Exception {
        when(mascotaService.listar(7L)).thenReturn(java.util.List.of(
                new CrearMascotaResponse(12L, 7L, "Rocky", "Perro", "Labrador", 3, "Café")));

        mockMvc.perform(get("/api/v1/afiliados/7/mascotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Rocky"));
    }

    @Test
    void actualizaMascotaSinCambiarSuAfiliado() throws Exception {
        when(mascotaService.actualizar(eq(12L), any())).thenReturn(
                new CrearMascotaResponse(12L, 7L, "Rocky", "Perro", "Labrador", 4, "Negro"));

        mockMvc.perform(put("/api/v1/mascotas/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest().replace("\"edad\": 3", "\"edad\": 4").replace("Café", "Negro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afiliadoId").value(7))
                .andExpect(jsonPath("$.edad").value(4));
    }

    @Test
    void responde404AlActualizarMascotaInexistente() throws Exception {
        when(mascotaService.actualizar(eq(999L), any())).thenThrow(new MascotaNotFoundException(999L));

        mockMvc.perform(put("/api/v1/mascotas/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void eliminaMascotaExistenteSinContenido() throws Exception {
        mockMvc.perform(delete("/api/v1/mascotas/12"))
                .andExpect(status().isNoContent());
    }

    @Test
    void responde404AlEliminarMascotaInexistente() throws Exception {
        org.mockito.Mockito.doThrow(new MascotaNotFoundException(999L))
                .when(mascotaService).eliminar(999L);

        mockMvc.perform(delete("/api/v1/mascotas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No se encontró la mascota con identificador 999."));
    }

    @Test
    void responde404CuandoElAfiliadoNoExiste() throws Exception {
        when(mascotaService.crear(eq(999L), any())).thenThrow(new AfiliadoNotFoundException(999L));

        mockMvc.perform(post("/api/v1/afiliados/999/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void responde400ParaUnaEspecieNoPermitida() throws Exception {
        mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest().replace("Perro", "Dragón")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("especie: Selecciona una especie válida."));
    }

        @Test
        void responde400CuandoFaltanCamposObligatoriosDeMascota() throws Exception {
                mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors").isArray());
        }

    @Test
    void responde400SiLaEdadNoEsUnNumeroEntero() throws Exception {
        mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest().replace("\"edad\": 3", "\"edad\": 3.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void responde400CuandoLaEdadEstaFueraDelRangoPermitido() throws Exception {
        mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest().replace("\"edad\": 3", "\"edad\": -1")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/afiliados/7/mascotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPetRequest().replace("\"edad\": 3", "\"edad\": 101")))
                .andExpect(status().isBadRequest());
    }

    private String validAffiliateRequest() {
        return """
                {
                  "numeroIdentificacion": "100",
                  "nombres": "Ana",
                  "apellidos": "Gomez",
                  "celular": "3001234567",
                  "correo": "ana@example.com",
                  "direccion": "Calle 1",
                  "autorizaTratamientoDatos": true
                }
                """;
    }

    private String validPetRequest() {
        return """
                {
                  "nombre": "Rocky",
                  "especie": "Perro",
                  "raza": "Labrador",
                  "edad": 3,
                  "color": "Café"
                }
                """;
    }

        private String validUpdateRequest() {
                return """
                                {
                                  "nombres": "Ana María",
                                  "apellidos": "Gomez",
                                  "celular": "3001234567",
                                  "correo": "ana@example.com",
                                  "direccion": "Calle 1"
                                }
                                """;
        }

        private AfiliadoDetalleResponse affiliateDetails() {
                return new AfiliadoDetalleResponse(7L, "100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1");
        }
}
