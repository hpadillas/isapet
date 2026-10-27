package com.isapet.service;

import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Plan;
import com.isapet.entity.VersionPlan;
import com.isapet.repository.PrestacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CupoAtencionesIncluidasTest {

    private static final long AFILIADO_ID = 7L;
    private static final long PLAN_ID = 3L;
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 20);
    private static final LocalDate HOY = LocalDate.of(2026, 3, 5);
    // El 5 de marzo cae en el periodo del 20 de febrero al 19 de marzo.
    private static final LocalDate PERIODO_DESDE = LocalDate.of(2026, 2, 20);
    private static final LocalDate PERIODO_HASTA_EXCLUSIVA = LocalDate.of(2026, 3, 20);

    @Mock
    private BeneficiosPlanResolver beneficiosResolver;

    @Mock
    private PrestacionRepository prestacionRepository;

    @InjectMocks
    private CupoAtencionesIncluidas cupoAtenciones;

    private Afiliacion afiliacion;

    @BeforeEach
    void prepararAfiliacion() {
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(PLAN_ID);
        afiliacion = mock(Afiliacion.class);
        when(afiliacion.getPlan()).thenReturn(plan);
        when(afiliacion.getFechaInicio()).thenReturn(INICIO);
    }

    @Test
    void sinVersionVigenteNoHayCupo() {
        when(beneficiosResolver.versionVigente(PLAN_ID, HOY)).thenReturn(Optional.empty());

        assertThat(cupoAtenciones.consultar(afiliacion, HOY)).isEmpty();
        verify(prestacionRepository, never()).contarIncluidasEnPlan(anyLong(), any(), any());
    }

    @Test
    void conAtencionesIlimitadasNoCuentaPrestaciones() {
        conLimite(null);

        UsoAtencionesIncluidas uso = cupoAtenciones.consultar(afiliacion, HOY).orElseThrow();

        assertThat(uso.ilimitadas()).isTrue();
        assertThat(uso.quedanDisponibles()).isTrue();
        assertThat(uso.siguienteEsLaUltima()).isFalse();
        assertThrows(IllegalStateException.class, uso::restantes);
        verify(prestacionRepository, never()).contarIncluidasEnPlan(anyLong(), any(), any());
    }

    @Test
    void cuentaLasIncluidasDelPeriodoQueContieneLaFecha() {
        conLimite(3);
        conUsadas(1);

        UsoAtencionesIncluidas uso = cupoAtenciones.consultar(afiliacion, HOY).orElseThrow();

        assertThat(uso.periodo()).isEqualTo(new PeriodoAfiliacion(PERIODO_DESDE, PERIODO_HASTA_EXCLUSIVA));
        assertThat(uso.limite()).isEqualTo(3);
        assertThat(uso.usadas()).isEqualTo(1);
        assertThat(uso.restantes()).isEqualTo(2);
        assertThat(uso.quedanDisponibles()).isTrue();
        assertThat(uso.siguienteEsLaUltima()).isFalse();
    }

    @Test
    void laSiguienteAtencionEsLaUltimaCuandoQuedaUna() {
        conLimite(3);
        conUsadas(2);

        UsoAtencionesIncluidas uso = cupoAtenciones.consultar(afiliacion, HOY).orElseThrow();

        assertThat(uso.restantes()).isEqualTo(1);
        assertThat(uso.quedanDisponibles()).isTrue();
        assertThat(uso.siguienteEsLaUltima()).isTrue();
    }

    @Test
    void conElCupoAgotadoNoQuedanDisponibles() {
        conLimite(3);
        conUsadas(3);

        UsoAtencionesIncluidas uso = cupoAtenciones.consultar(afiliacion, HOY).orElseThrow();

        assertThat(uso.restantes()).isZero();
        assertThat(uso.quedanDisponibles()).isFalse();
        assertThat(uso.siguienteEsLaUltima()).isFalse();
    }

    private void conLimite(Integer limite) {
        VersionPlan version = mock(VersionPlan.class);
        when(version.getLimiteAtenciones()).thenReturn(limite);
        when(beneficiosResolver.versionVigente(PLAN_ID, HOY)).thenReturn(Optional.of(version));
    }

    private void conUsadas(long usadas) {
        Afiliado afiliado = mock(Afiliado.class);
        when(afiliado.getId()).thenReturn(AFILIADO_ID);
        when(afiliacion.getAfiliado()).thenReturn(afiliado);
        when(prestacionRepository.contarIncluidasEnPlan(AFILIADO_ID, PERIODO_DESDE, PERIODO_HASTA_EXCLUSIVA))
                .thenReturn(usadas);
    }
}
