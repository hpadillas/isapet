package com.isapet.service;

import com.isapet.dto.CrearMascotaRequest;
import com.isapet.dto.CrearMascotaResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.MascotaNotFoundException;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import com.isapet.mapper.MascotaMapper;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MascotaServiceTest {

    @Mock
    private AfiliadoRepository afiliadoRepository;

    @Mock
    private MascotaRepository mascotaRepository;

    @Spy
    private MascotaMapper mascotaMapper = new MascotaMapper();

    @InjectMocks
    private MascotaService mascotaService;

    @Test
    void registraMascotaAsociadaAlAfiliadoExistente() {
        Afiliado afiliado = new Afiliado("100", "Ana", "Gomez");
        CrearMascotaRequest request = new CrearMascotaRequest("Rocky", "Perro", "Labrador", 3, "Café");
        when(afiliadoRepository.findById(7L)).thenReturn(Optional.of(afiliado));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CrearMascotaResponse response = mascotaService.crear(7L, request);

        ArgumentCaptor<Mascota> captor = ArgumentCaptor.forClass(Mascota.class);
        verify(mascotaRepository).save(captor.capture());
        assertThat(captor.getValue().getAfiliado()).isSameAs(afiliado);
        assertThat(captor.getValue().getEdad()).isEqualTo(3);
        assertThat(response.afiliadoId()).isEqualTo(afiliado.getId());
    }

    @Test
    void rechazaCrearMascotaParaAfiliadoInexistente() {
        when(afiliadoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AfiliadoNotFoundException.class, () -> mascotaService.crear(
                999L, new CrearMascotaRequest("Rocky", "Perro", "Labrador", 3, "Café")));

        verify(mascotaRepository, times(0)).save(any(Mascota.class));
    }

    @Test
    void permiteRegistrarVariasMascotasParaElMismoAfiliado() {
        Afiliado afiliado = new Afiliado("100", "Ana", "Gomez");
        when(afiliadoRepository.findById(7L)).thenReturn(Optional.of(afiliado));
        when(mascotaRepository.save(any(Mascota.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mascotaService.crear(7L, new CrearMascotaRequest("Rocky", "Perro", "Labrador", 3, "Café"));
        mascotaService.crear(7L, new CrearMascotaRequest("Luna", "Gato", "Criollo", 2, "Gris"));

        verify(mascotaRepository, times(2)).save(any(Mascota.class));
    }

    @Test
    void actualizaDatosDeMascotaSinCambiarElAfiliado() {
        Afiliado afiliado = new Afiliado("100", "Ana", "Gomez");
        Mascota mascota = new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado);
        when(mascotaRepository.findById(12L)).thenReturn(Optional.of(mascota));

        CrearMascotaResponse response = mascotaService.actualizar(12L,
                new CrearMascotaRequest("Rocky", "Perro", "Labrador", 4, "Negro"));

        assertThat(response.afiliadoId()).isEqualTo(afiliado.getId());
        assertThat(response.edad()).isEqualTo(4);
        assertThat(response.color()).isEqualTo("Negro");
    }

    @Test
    void actualizarMascotaInexistenteDevuelveNotFound() {
        when(mascotaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(MascotaNotFoundException.class, () -> mascotaService.actualizar(404L,
                new CrearMascotaRequest("Rocky", "Perro", "Labrador", 4, "Negro")));
    }
}
