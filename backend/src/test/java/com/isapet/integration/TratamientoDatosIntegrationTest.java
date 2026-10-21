package com.isapet.integration;

import com.isapet.dto.AutorizacionDatosResponse;
import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.entity.Afiliado;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.service.AfiliadoService;
import com.isapet.service.AutorizacionDatosService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ley 1581 de 2012: aviso de privacidad, autorización obligatoria al registrar y prueba de la autorización. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TratamientoDatosIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AfiliadoService afiliadoService;
    @Autowired private AutorizacionDatosService autorizacionService;
    @Autowired private AfiliadoRepository afiliadoRepository;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void elAvisoDePrivacidadEsPublicoEInformaLoQueExigeElArticulo12() throws Exception {
        mockMvc.perform(get("/api/v1/privacidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.responsable").isNotEmpty())
                .andExpect(jsonPath("$.correo").isNotEmpty())
                .andExpect(jsonPath("$.finalidades").isNotEmpty())
                .andExpect(jsonPath("$.derechos", hasSize(6)))
                .andExpect(jsonPath("$.diasHabilesConsultas").value(10))
                .andExpect(jsonPath("$.diasHabilesReclamos").value(15));
    }

    @Test
    void sinLaAutorizacionDelTitularNoSePuedeRegistrar() {
        assertThat(validator.validate(request(null))).isNotEmpty();
        assertThat(validator.validate(request(false)))
                .anySatisfy(error -> assertThat(error.getMessage()).contains("autorización del titular"));
        assertThat(validator.validate(request(true))).isEmpty();
    }

    @Test
    void alRegistrarSeGuardaLaPruebaDeLaAutorizacionConLaVersionDeLaPolitica() {
        CrearAfiliadoResponse creado = afiliadoService.crear(request(true));

        AutorizacionDatosResponse autorizacion = autorizacionService.consultar(creado.id());

        assertThat(autorizacion.registrada()).isTrue();
        assertThat(autorizacion.versionPolitica()).isEqualTo("1.0");
        assertThat(autorizacion.otorgadaEn()).isNotNull();
        assertThat(autorizacion.registradaPor()).isNotBlank();
        assertThat(autorizacion.medio()).contains("Formulario de registro");
    }

    @Test
    void unAfiliadoAnteriorPuedeRegistrarSuAutorizacionUnaSolaVez() {
        Afiliado anterior = afiliadoRepository.saveAndFlush(new Afiliado(
                "AD-" + System.nanoTime(), "Luis", "Pérez", "3001234567", "luis@example.com", "Calle 2"));
        assertThat(autorizacionService.consultar(anterior.getId()).registrada()).isFalse();

        AutorizacionDatosResponse primera = autorizacionService.registrarPosterior(anterior.getId());
        AutorizacionDatosResponse segunda = autorizacionService.registrarPosterior(anterior.getId());

        assertThat(primera.registrada()).isTrue();
        assertThat(segunda.otorgadaEn()).isEqualTo(primera.otorgadaEn());
    }

    @Test
    void consultarLaAutorizacionRequiereSesionAdministrativa() throws Exception {
        mockMvc.perform(get("/api/v1/afiliados/1/autorizacion-datos")).andExpect(status().isUnauthorized());
    }

    private static CrearAfiliadoRequest request(Boolean autoriza) {
        return new CrearAfiliadoRequest(String.valueOf(System.nanoTime() % 1_000_000_000L), "Ana", "Gómez",
                "3001234567", "ana@example.com", "Calle 1", autoriza);
    }
}
