package com.isapet.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AfiliadoContactValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void documentoYAcelularAceptanSoloDigitos() {
        assertThat(valid(create("123456789", "Juan", "Pérez", "3001234567", "usuario@gmail.com", "Calle 10 # 20-30"))).isTrue();
        for (String value : new String[]{"123ABC", "123-456", "123.456", "ABC123", " 123", "123 "}) {
            assertThat(valid(create(value, "Juan", "Pérez", "3001234567", "usuario@gmail.com", "Calle 1")))
                    .as("documento %s", value).isFalse();
        }
        for (String value : new String[]{"300-123-4567", "300 123 4567", "+573001234567", "ABC300123"}) {
            assertThat(valid(create("6", "Juan", "Pérez", value, "usuario@gmail.com", "Calle 1")))
                    .as("celular %s", value).isFalse();
        }
    }

    @Test
    void nombresUnicodeEspaciosYTienenExcepcionExplicitaParaSeis() {
        for (String value : new String[]{"Juan", "María", "María José", "José María", "Ñusta", "6", "Juan 6", "6 Juan"}) {
            assertThat(valid(create("123", value, "Pérez", "3001234567", "usuario@gmail.com", "Calle 1")))
                    .as("nombre %s", value).isTrue();
        }
        for (String value : new String[]{"Juan123", "Juan2", "Juan@", "Juan#", "Juan-123", "Juan_123"}) {
            assertThat(valid(create("123", value, "Pérez", "3001234567", "usuario@gmail.com", "Calle 1")))
                    .as("nombre %s", value).isFalse();
        }
    }

    @Test
    void apellidosSoloAdmitenLetrasYEspaciosSinLaExcepcionDelSeis() {
        for (String value : new String[]{"Pérez", "Gómez", "De la Cruz", "Ñusta"}) {
            assertThat(valid(create("123", "Juan", value, "3001234567", "usuario@gmail.com", "Calle 1")))
                    .as("apellido %s", value).isTrue();
        }
        for (String value : new String[]{"6", "Pérez 6", "Perez2", "Gomez@", "De-la-Cruz", "Gomez_1"}) {
            assertThat(valid(create("123", "Juan", value, "3001234567", "usuario@gmail.com", "Calle 1")))
                    .as("apellido %s", value).isFalse();
        }
    }

    @Test
    void correoSeValidaYDireccionAdmitePuntuacionHabitual() {
        for (String email : new String[]{"usuario@gmail.com", "nombre.apellido@dominio.com", "usuario123@empresa.co"}) {
            assertThat(valid(create("123", "Juan", "Pérez", "3001234567", email, "Calle 10 # 20-30")))
                    .as("correo %s", email).isTrue();
        }
        for (String email : new String[]{"usuario@", "@gamil.com", "usuario@gmail", "usuario.com", "usuario @gmail.com"}) {
            assertThat(valid(create("123", "Juan", "Pérez", "3001234567", email, "Calle 1")))
                    .as("correo %s", email).isFalse();
        }
        for (String address : new String[]{"Calle 10 # 20-30", "Cra. 12 # 10-25 Apt 302", "Av. Bolívar # 5-20", "Casa 4, Barrio Centro"}) {
            assertThat(valid(create("123", "Juan", "Pérez", "3001234567", "usuario@gmail.com", address)))
                    .as("dirección %s", address).isTrue();
        }
    }

    @Test
    void dtoDeActualizacionAplicaLasMismasReglasANombresYCelular() {
        ActualizarAfiliadoRequest valid = new ActualizarAfiliadoRequest(
                "María José", "De la Cruz", "3001234567", "maria@example.com", "Calle 6A No. 10-25");
        assertThat(validator.validate(valid)).isEmpty();

        ActualizarAfiliadoRequest invalid = new ActualizarAfiliadoRequest(
                "Nombre2", "Apellido-", "300 123", "maria@example.com", "Calle 1");
        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    private boolean valid(CrearAfiliadoRequest request) {
        return validator.validate(request).isEmpty();
    }

    private CrearAfiliadoRequest create(String document, String names, String surnames, String phone, String email, String address) {
        return new CrearAfiliadoRequest(document, names, surnames, phone, email, address, true);
    }
}
