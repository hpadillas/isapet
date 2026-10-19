package com.isapet.repository;

import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.service.MascotaService;
import com.isapet.mapper.MascotaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenciaF01Test {

    @Autowired
    private AfiliadoRepository afiliadoRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Test
    void afiliadoPuedePersistirse() {
        Afiliado afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("123456789", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1"));

        assertThat(afiliado.getId()).isNotNull();
    }

    @Test
    void mascotaPuedePersistirseAsociadaAUnAfiliado() {
        Afiliado afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("123456790", "Luis", "Perez"));

        Mascota mascota = mascotaRepository.saveAndFlush(
                new Mascota("Luna", "Perro", "Labrador", 3, "Café", afiliado));

        assertThat(mascota.getId()).isNotNull();
        assertThat(mascota.getAfiliado().getId()).isEqualTo(afiliado.getId());
    }

    @Test
    void variasMascotasPuedenAsociarseAlMismoAfiliado() {
        Afiliado afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("123456792", "Eva", "Lopez", "3000000000", "eva@example.com", "Carrera 2"));

        Mascota primera = mascotaRepository.saveAndFlush(
                new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        Mascota segunda = mascotaRepository.saveAndFlush(
                new Mascota("Luna", "Gato", "Criollo", 2, "Gris", afiliado));

        assertThat(primera.getAfiliado().getId()).isEqualTo(afiliado.getId());
        assertThat(segunda.getAfiliado().getId()).isEqualTo(afiliado.getId());
    }

    @Test
    void eliminarMascotaConservaAlAfiliadoYLasmacotasHermanas() {
        Afiliado afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("F01-DELETE-PET", "Elena", "Prueba", "3001234567", "delete-pet@example.com", "Calle 3"));
        Mascota mascotaEliminada = mascotaRepository.saveAndFlush(
                new Mascota("Nina", "Gato", "Criollo", 4, "Gris", afiliado));
        Mascota mascotaConservada = mascotaRepository.saveAndFlush(
                new Mascota("Toby", "Perro", "Criollo", 5, "Negro", afiliado));
        Long afiliadoId = afiliado.getId();
        Long mascotaEliminadaId = mascotaEliminada.getId();
        Long mascotaConservadaId = mascotaConservada.getId();

        new MascotaService(afiliadoRepository, mascotaRepository, new MascotaMapper()).eliminar(mascotaEliminadaId);
        mascotaRepository.flush();

        assertThat(mascotaRepository.findById(mascotaEliminadaId)).isEmpty();
        assertThat(afiliadoRepository.findById(afiliadoId)).isPresent();
        assertThat(mascotaRepository.findAllByAfiliadoIdOrderByIdAsc(afiliadoId))
                .extracting(Mascota::getId)
                .containsExactly(mascotaConservadaId);
    }

    @Test
    void mascotaSinAfiliadoNoPuedePersistirse() {
        assertThrows(DataAccessException.class, () -> mascotaRepository.saveAndFlush(
                new Mascota("Luna", "Perro", null)));
    }

    @Test
    void noSePermiteDuplicarNumeroIdentificacion() {
        afiliadoRepository.saveAndFlush(
                new Afiliado("123456791", "Ana", "Gomez"));

        assertThrows(DataAccessException.class, () -> afiliadoRepository.saveAndFlush(
                new Afiliado("123456791", "Luis", "Perez")));
    }

    @Test
    void busquedaPaginadaIncluyeElConteoRealDeMascotas() {
        Afiliado afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("F01-PAGE-1", "Busqueda", "Unica", "3001234567", "busqueda@example.com", "Calle 1"));
        mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        mascotaRepository.saveAndFlush(new Mascota("Luna", "Gato", "Criollo", 2, "Gris", afiliado));

        Page<com.isapet.dto.AfiliadoListaResponse> resultados = afiliadoRepository.buscar(
                "busqueda@example.com", PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(resultados.getTotalElements()).isEqualTo(1);
        assertThat(resultados.getContent().get(0).cantidadMascotas()).isEqualTo(2);
    }

    @Test
    void busquedaVaciaDevuelveLaPrimeraPaginaCompleta() {
        afiliadoRepository.saveAndFlush(
                new Afiliado("F01-PAGE-EMPTY", "Sin", "Mascotas", "3001234567", "sinmascota@example.com", "Calle 2"));

        Page<com.isapet.dto.AfiliadoListaResponse> resultados = afiliadoRepository.buscar(
                "", PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(resultados.getContent()).isNotEmpty();
    }
}