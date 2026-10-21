package com.isapet.dto;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Campos de palabras sin números (salvo el 6 del nombre de persona) y campos numéricos solo enteros. */
class CamposDePalabrasYNumerosValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final JsonMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void nombreRazaYColorDeLaMascotaSoloAdmitenPalabras() {
        assertThat(validator.validate(new CrearMascotaRequest("Señor Bigotes", "Gato", "Criollo de pelo corto", 2, "Café claro")))
                .isEmpty();

        assertThat(validator.validate(new CrearMascotaRequest("Max2", "Perro", "Labrador", 3, "Negro"))).isNotEmpty();
        assertThat(validator.validate(new CrearMascotaRequest("6", "Perro", "Labrador", 3, "Negro")))
                .as("el 6 solo se admite en el nombre de persona").isNotEmpty();
        assertThat(validator.validate(new CrearMascotaRequest("Max", "Perro", "Labrador 2", 3, "Negro"))).isNotEmpty();
        assertThat(validator.validate(new CrearMascotaRequest("Max", "Perro", "Labrador", 3, "#000000"))).isNotEmpty();
    }

    @Test
    void elNombreDelPlanYElPrestadorSoloAdmitenPalabras() {
        LocalDate hoy = LocalDate.now();
        assertThat(validator.validate(new CrearPlanRequest("ORO", "Plan Oro", BigDecimal.TEN, 1, 2, hoy))).isEmpty();
        assertThat(validator.validate(new CrearPlanRequest("ORO", "Plan 2026", BigDecimal.TEN, 1, 2, hoy))).isNotEmpty();

        assertThat(validator.validate(prestacion("Clínica Veterinaria San José"))).isEmpty();
        assertThat(validator.validate(prestacion("Vet 24 horas"))).isNotEmpty();
        assertThat(validator.validate(prestacion("Dr. Pérez"))).isNotEmpty();
    }

    @Test
    void losCamposEnterosRechazanDecimalesYTexto() throws Exception {
        String base = """
                {"codigo":"ORO","nombre":"Plan Oro","precioMensual":1000,"maximoMascotas":%s,
                 "limiteAtenciones":null,"vigenteDesde":"2026-10-21"}""";

        assertThat(json.readValue(base.formatted("2"), CrearPlanRequest.class).maximoMascotas()).isEqualTo(2);
        for (String invalido : new String[]{"2.7", "\"2\"", "\"dos\"", "true"}) {
            assertThatThrownBy(() -> json.readValue(base.formatted(invalido), CrearPlanRequest.class))
                    .as("maximoMascotas = %s", invalido)
                    .isInstanceOf(JsonMappingException.class)
                    .hasMessageContaining("Debe ser un número entero");
        }
        assertThatThrownBy(() -> json.readValue(
                "{\"cantidadMascotas\":1,\"servicios\":[{\"servicioId\":1,\"cantidadMensual\":1.5}]}",
                SimulacionPlanRequest.class))
                .isInstanceOf(JsonMappingException.class);
    }

    private static RegistrarPrestacionRequest prestacion(String prestador) {
        return new RegistrarPrestacionRequest(1L, 1L, LocalDate.now(), prestador, null, null);
    }
}
