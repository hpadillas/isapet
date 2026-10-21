package com.isapet.integration;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CuotaPlanResponse;
import com.isapet.dto.HistorialPagosResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.dto.ResumenEliminacionAfiliadoResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.MedioPago;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.EliminacionAfiliadoService;
import com.isapet.service.PrestacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cambio de plan programado, cuotas mensuales y eliminación del afiliado. Cada prueba se revierte. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CambioPlanPagosYEliminacionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private AfiliacionRepository afiliacionRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private ServicioVeterinarioRepository servicioRepository;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private BonoService bonoService;
    @Autowired private PrestacionService prestacionService;
    @Autowired private EliminacionAfiliadoService eliminacionService;

    private final LocalDate hoy = LocalDate.now();
    private Afiliado afiliado;
    private Mascota rocky;
    private Mascota luna;

    @BeforeEach
    void prepararAfiliado() {
        afiliado = afiliadoRepository.saveAndFlush(new Afiliado(
                "CP-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana@example.com", "Calle 1"));
        rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        luna = mascotaRepository.saveAndFlush(new Mascota("Luna", "Gato", "Criollo", 2, "Gris", afiliado));
        asignar("BASICO", rocky);
    }

    // Cambio de plan ------------------------------------------------------

    @Test
    void cambiarDePlanLoProgramaParaElDiaSiguienteAlFinDelPeriodo() {
        PlanAfiliadoResponse plan = asignar("ESTANDAR", rocky, luna);

        assertThat(plan.codigo()).isEqualTo("BASICO");
        assertThat(plan.mascotasCubiertas()).extracting("nombre").containsExactly("Rocky");
        assertThat(plan.periodoHasta()).isEqualTo(hoy.plusMonths(1).minusDays(1));
        assertThat(plan.cambioProgramado()).isNotNull();
        assertThat(plan.cambioProgramado().codigo()).isEqualTo("ESTANDAR");
        assertThat(plan.cambioProgramado().fechaActivacion()).isEqualTo(hoy.plusMonths(1));
        assertThat(plan.cambioProgramado().precioMensual()).isEqualByComparingTo("100000");
        assertThat(plan.cambioProgramado().mascotas()).extracting("nombre").containsExactly("Rocky", "Luna");
    }

    @Test
    void elCambioSeActivaEnSuFechaConLasMascotasElegidas() {
        asignar("ESTANDAR", rocky, luna);
        LocalDate fechaCambio = hoy.plusMonths(1);

        assertThat(afiliacionService.aplicarCambiosProgramados(fechaCambio.minusDays(1))).isZero();
        assertThat(afiliacionService.aplicarCambiosProgramados(fechaCambio)).isEqualTo(1);

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());
        assertThat(plan.codigo()).isEqualTo("ESTANDAR");
        assertThat(plan.fechaInicio()).isEqualTo(fechaCambio);
        assertThat(plan.mascotasCubiertas()).extracting("nombre").containsExactly("Rocky", "Luna");
        assertThat(plan.cambioProgramado()).isNull();
        // Una cuota por periodo: la del plan anterior y la del primer periodo del nuevo.
        assertThat(cuotaService.historial(afiliado.getId()).cuotas())
                .extracting(CuotaPlanResponse::planCodigo, CuotaPlanResponse::periodoDesde)
                .containsExactly(tuple("ESTANDAR", fechaCambio), tuple("BASICO", hoy));
    }

    @Test
    void elCambioProgramadoSePuedeCancelar() {
        asignar("ESTANDAR", rocky, luna);

        PlanAfiliadoResponse plan = afiliacionService.cancelarCambioProgramado(afiliado.getId());

        assertThat(plan.cambioProgramado()).isNull();
        assertThat(afiliacionService.aplicarCambiosProgramados(hoy.plusMonths(1))).isZero();
        assertThrows(BusinessRuleException.class, () -> afiliacionService.cancelarCambioProgramado(afiliado.getId()));
    }

    @Test
    void cambiarSoloLasMascotasDelMismoPlanAplicaDeInmediato() {
        PlanAfiliadoResponse plan = asignar("BASICO", luna);

        assertThat(plan.mascotasCubiertas()).extracting("nombre").containsExactly("Luna");
        assertThat(plan.fechaInicio()).isEqualTo(hoy);
        assertThat(plan.cambioProgramado()).isNull();
    }

    @Test
    void elCambioProgramadoRespetaElMaximoDeMascotasDelNuevoPlan() {
        asignar("ESTANDAR", rocky, luna);

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> asignar("BASICO", rocky, luna));
        assertThat(error.getMessage()).contains("máximo 1");
    }

    // Pagos ---------------------------------------------------------------

    @Test
    void cadaPeriodoGeneraUnaCuotaPendienteQueSePuedePagarUnaVez() {
        // La primera cuota (activación) ya se pagó al asignar; el periodo siguiente genera una pendiente.
        var afiliacion = afiliacionRepository.findByAfiliadoId(afiliado.getId()).orElseThrow();
        cuotaService.generarCuotas(afiliacion, hoy.plusMonths(1));
        HistorialPagosResponse historial = cuotaService.historial(afiliado.getId());
        assertThat(historial.cuotas()).hasSize(2);
        CuotaPlanResponse siguiente = historial.cuotas().get(0);
        assertThat(siguiente.estado()).isEqualTo("PENDIENTE");
        assertThat(siguiente.deActivacion()).isFalse();
        assertThat(siguiente.valor()).isEqualByComparingTo("60000");
        assertThat(siguiente.periodoDesde()).isEqualTo(hoy.plusMonths(1));
        assertThat(siguiente.fechaLimitePago()).isEqualTo(hoy.plusMonths(1).plusDays(4));
        Long cuotaId = siguiente.id();

        CuotaPlanResponse pagada = cuotaService.registrarPago(
                afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.TRANSFERENCIA));

        assertThat(pagada.estado()).isEqualTo("PAGADA");
        assertThat(pagada.medioPago()).isEqualTo("TRANSFERENCIA");
        assertThat(cuotaService.historial(afiliado.getId()).totalPagado()).isEqualByComparingTo("120000");
        assertThrows(BusinessRuleException.class, () -> cuotaService.registrarPago(
                afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.EFECTIVO)));
    }

    @Test
    void lasCuotasNoSeDuplicanYCubrenCadaPeriodoTranscurrido() {
        var afiliacion = afiliacionRepository.findByAfiliadoId(afiliado.getId()).orElseThrow();

        cuotaService.generarCuotas(afiliacion, hoy.plusMonths(2));
        cuotaService.generarCuotas(afiliacion, hoy.plusMonths(2));

        HistorialPagosResponse historial = cuotaService.historial(afiliado.getId());
        assertThat(historial.cuotas()).extracting(CuotaPlanResponse::periodoDesde)
                .containsExactly(hoy.plusMonths(2), hoy.plusMonths(1), hoy);
        // La primera ya está pagada (activación); quedan pendientes las dos siguientes.
        assertThat(historial.totalPendiente()).isEqualByComparingTo("120000");
    }

    // Eliminación ---------------------------------------------------------

    @Test
    void eliminarAfiliadoBorraMascotasHistoriaClinicaBonosYPagos() {
        Long consulta = servicio("CONSULTA_GENERAL");
        for (int i = 0; i < 2; i++) {
            prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                    rocky.getId(), consulta, hoy, "VetCasa", null, null));
        }
        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));
        asignar("ESTANDAR", rocky, luna);

        ResumenEliminacionAfiliadoResponse resumen = eliminacionService.resumen(afiliado.getId());
        assertThat(resumen.plan()).isEqualTo("Básico");
        assertThat(resumen.mascotas()).containsExactly("Rocky", "Luna");
        assertThat(resumen.atenciones()).isEqualTo(2);
        assertThat(resumen.bonos()).isEqualTo(1);
        assertThat(resumen.cuotas()).isEqualTo(1);

        eliminacionService.eliminar(afiliado.getId());

        assertThat(afiliadoRepository.existsById(afiliado.getId())).isFalse();
        assertThat(mascotaRepository.findAllByAfiliadoIdOrderByIdAsc(afiliado.getId())).isEmpty();
        assertThat(afiliacionRepository.findByAfiliadoId(afiliado.getId())).isEmpty();
        assertThrows(AfiliadoNotFoundException.class, () -> cuotaService.historial(afiliado.getId()));
        assertThrows(AfiliadoNotFoundException.class, () -> eliminacionService.eliminar(afiliado.getId()));
    }

    @Test
    void eliminarAfiliadoRequiereSesionAdministrativa() throws Exception {
        mockMvc.perform(delete("/api/v1/afiliados/{id}", afiliado.getId()))
                .andExpect(status().isForbidden());
        assertThat(afiliadoRepository.existsById(afiliado.getId())).isTrue();
    }

    private PlanAfiliadoResponse asignar(String codigoPlan, Mascota... mascotas) {
        Long planId = planRepository.findAllByOrderByCodigoAsc().stream()
                .filter(plan -> plan.getCodigo().equalsIgnoreCase(codigoPlan))
                .findFirst().orElseThrow().getId();
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                planId, java.util.Arrays.stream(mascotas).map(Mascota::getId).toList()));
        ActivacionPlan.pagarActivacion(cuotaService, afiliado.getId());
        return afiliacionService.obtenerPlan(afiliado.getId());
    }

    private Long servicio(String codigo) {
        return servicioRepository.findAll().stream()
                .filter(servicio -> servicio.getCodigo().equals(codigo))
                .findFirst().orElseThrow().getId();
    }
}
