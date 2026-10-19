package com.isapet.service;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.ActualizarAfiliadoRequest;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.entity.Afiliado;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.DuplicateNumeroIdentificacionException;
import com.isapet.repository.AfiliadoRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import com.isapet.mapper.AfiliadoMapper;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AfiliadoServiceTest {

    @Mock
    private AfiliadoRepository afiliadoRepository;

    @Spy
    private AfiliadoMapper afiliadoMapper = new AfiliadoMapper();

    @InjectMocks
    private AfiliadoService afiliadoService;

    @Test
    void registraAfiliadoConSusDatosDeContacto() {
        CrearAfiliadoRequest request = request("100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1");
        Afiliado afiliado = new Afiliado("100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1");

        when(afiliadoRepository.existsByNumeroIdentificacion("100")).thenReturn(false);
        when(afiliadoRepository.save(any(Afiliado.class))).thenReturn(afiliado);

        CrearAfiliadoResponse response = afiliadoService.crear(request);

        assertThat(response.numeroIdentificacion()).isEqualTo("100");
        assertThat(response.celular()).isEqualTo("3001234567");
        assertThat(response.correo()).isEqualTo("ana@example.com");
        assertThat(response.direccion()).isEqualTo("Calle 1");
        verify(afiliadoRepository).save(any(Afiliado.class));
    }

    @Test
    void rechazaNumeroIdentificacionDuplicado() {
        CrearAfiliadoRequest request = request("100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1");
        when(afiliadoRepository.existsByNumeroIdentificacion("100")).thenReturn(true);

        assertThrows(DuplicateNumeroIdentificacionException.class,
                () -> afiliadoService.crear(request));

        verify(afiliadoRepository, org.mockito.Mockito.never()).save(any(Afiliado.class));
    }

        @Test
        void obtieneAfiliadoExistenteYActualizaSinModificarIdentificacion() {
        Afiliado afiliado = new Afiliado("100", "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1");
        when(afiliadoRepository.findById(7L)).thenReturn(Optional.of(afiliado));

        AfiliadoDetalleResponse updated = afiliadoService.actualizar(7L,
            new ActualizarAfiliadoRequest("Ana Maria", "Gomez", "3007654321", "nuevo@example.com", "Calle 2"));

        assertThat(updated.nombres()).isEqualTo("Ana Maria");
        assertThat(updated.numeroIdentificacion()).isEqualTo("100");
        assertThat(updated.correo()).isEqualTo("nuevo@example.com");
        }

        @Test
        void afiliadoInexistenteDevuelveNotFoundAlConsultarYActualizar() {
        when(afiliadoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(AfiliadoNotFoundException.class, () -> afiliadoService.obtener(404L));
        assertThrows(AfiliadoNotFoundException.class, () -> afiliadoService.actualizar(404L,
            new ActualizarAfiliadoRequest("Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1")));
        }

        @Test
        void listaPermiteBusquedaVaciaYBuscaPorEmail() {
        when(afiliadoRepository.buscar(org.mockito.ArgumentMatchers.eq(""), any()))
            .thenReturn(new PageImpl<>(List.of()));
        when(afiliadoRepository.buscar(org.mockito.ArgumentMatchers.eq("ana@example.com"), any()))
            .thenReturn(new PageImpl<>(List.of(new AfiliadoListaResponse(
                1L, "100", "Ana", "Gomez", "300", "ana@example.com", "Calle 1", 0L))));

        assertThat(afiliadoService.listar(" ", PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(afiliadoService.listar(" ana@example.com ", PageRequest.of(0, 10)).getContent())
            .hasSize(1);
        }

    private CrearAfiliadoRequest request(
            String numeroIdentificacion,
            String nombres,
            String apellidos,
            String celular,
            String correo,
            String direccion) {
        return new CrearAfiliadoRequest(
                numeroIdentificacion, nombres, apellidos, celular, correo, direccion);
    }
}