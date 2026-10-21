package com.isapet.integration;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CancelacionPlanResponse;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.CuotaPlanResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.PlanResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.MedioPago;
import com.isapet.exception.AfiliacionNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.CancelacionPlanService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.EventoAfiliado;
import com.isapet.service.NotificacionService;
import com.isapet.service.PlanService;
import com.isapet.service.PrestacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plazo de pago de 4 días, suspensión y reactivación, y cancelación del plan. Usa un plan propio vigente
 * desde hace 60 días para poder simular afiliaciones que empezaron en el pasado. Cada prueba se revierte.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class CancelacionYSuspensionIntegrationTest {

    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private AfiliacionRepository afiliacionRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private CuotaPlanRepository cuotaRepository;
    @Autowired private ServicioVeterinarioRepository servicioRepository;
    @Autowired private PlanService planService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private CancelacionPlanService cancelacionService;
    @Autowired private BonoService bonoService;
    @Autowired private PrestacionService prestacionService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private ApplicationEvents eventos;

    private final LocalDate hoy = LocalDate.now();
    private PlanResponse plan;
    private Afiliado afiliado;
    private Mascota rocky;

    @BeforeEach
    void preparar() {
        plan = planService.crearPlan(new CrearPlanRequest(
                "TST" + System.nanoTime() % 100000, "Plan Prueba", new BigDecimal("50000"), 2, 2, hoy.minusDays(60)));
        afiliado = afiliadoRepository.saveAndFlush(new Afiliado(
                "CS-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana@example.com", "Calle 1"));
        rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
    }

    // Plazo de pago y suspensión ------------------------------------------

    @Test
    void elPlazoDePagoEsDeCuatroDiasDespuesDelInicioDelPeriodo() {
        afiliarHoy();
        pagarCuotaActual();
        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoId(afiliado.getId()).orElseThrow();
        LocalDate siguientePeriodo = hoy.plusMonths(1);
        cuotaService.generarCuotas(afiliacion, siguientePeriodo);
        CuotaPlanResponse cuota = cuotaService.historial(afiliado.getId()).cuotas().get(0);

        assertThat(cuota.periodoDesde()).isEqualTo(siguientePeriodo);
        assertThat(cuota.fechaLimitePago()).isEqualTo(siguientePeriodo.plusDays(4));
        assertThat(cuotaRepository.planSuspendido(afiliado.getId(), siguientePeriodo.plusDays(4))).isFalse();
        assertThat(cuotaRepository.planSuspendido(afiliado.getId(), siguientePeriodo.plusDays(5))).isTrue();
    }

    // Activación al pagar ---------------------------------------------------

    @Test
    void alAsignarElPlanQuedaPendienteDeActivacionSinBeneficios() {
        afiliarHoy();

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());
        assertThat(plan.estado()).isEqualTo("PENDIENTE_ACTIVACION");
        assertThat(plan.cuotaPendiente().deActivacion()).isTrue();
        assertThat(plan.cuotaPendiente().fechaLimitePago()).isNull();
        assertThat(eventos.stream(EventoAfiliado.PlanActivado.class)).isEmpty();
        // Sin atenciones incluidas ni bonos hasta pagar.
        assertThat(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, null)).cobertura()).isEqualTo("PARTICULAR");
        BusinessRuleException bono = assertThrows(BusinessRuleException.class, () -> bonoService.comprar(
                afiliado.getId(), new CompraBonoRequest(rocky.getId(), consultaGeneral(), MedioPago.EFECTIVO)));
        assertThat(bono.getMessage()).contains("aún no está activo");
        // La cuota de activación no vence: no suspende ni genera más cuotas aunque pase el tiempo.
        assertThat(cuotaRepository.planSuspendido(afiliado.getId(), hoy.plusDays(40))).isFalse();
        cuotaService.generarCuotas(afiliacionRepository.findByAfiliadoId(afiliado.getId()).orElseThrow(), hoy.plusMonths(2));
        assertThat(cuotaService.historial(afiliado.getId()).cuotas()).hasSize(1);
    }

    @Test
    void alPagarLaPrimeraCuotaElPlanSeActivaDesdeEseDia() {
        afiliarHoy();

        pagarCuotaActual();

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());
        assertThat(plan.estado()).isEqualTo("ACTIVO");
        assertThat(plan.fechaInicio()).isEqualTo(hoy);
        assertThat(plan.periodoHasta()).isEqualTo(hoy.plusMonths(1).minusDays(1));
        assertThat(eventos.stream(EventoAfiliado.PlanActivado.class)).hasSize(1);
        assertThat(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, null)).cobertura()).isEqualTo("PLAN");
    }

    @Test
    void unPlanSinActivarNoSePuedeCambiarPorOtro() {
        afiliarHoy();
        PlanResponse otro = planService.crearPlan(new CrearPlanRequest(
                "OTA" + System.nanoTime() % 100000, "Plan Otro", new BigDecimal("70000"), 2, 3, hoy.minusDays(60)));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> afiliacionService.asignarPlan(
                afiliado.getId(), new AsignarPlanRequest(otro.id(), List.of(rocky.getId()))));
        assertThat(error.getMessage()).contains("aún no se ha activado");
    }

    @Test
    void conLaCuotaVencidaElPlanSeSuspendeYSeAvisaUnaVez() {
        afiliarDesdeHaceDiezDias();

        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).estado()).isEqualTo("SUSPENDIDO");
        BusinessRuleException bono = assertThrows(BusinessRuleException.class, () -> bonoService.comprar(
                afiliado.getId(), new CompraBonoRequest(rocky.getId(), consultaGeneral(), MedioPago.EFECTIVO)));
        assertThat(bono.getMessage()).contains("suspendido por falta de pago");
        // Sin atenciones incluidas mientras esté suspendido.
        assertThat(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, null)).cobertura()).isEqualTo("PARTICULAR");

        notificacionService.notificarSuspensiones(hoy);
        notificacionService.notificarSuspensiones(hoy);
        assertThat(notificacionService.listar(afiliado.getId()))
                .filteredOn(notificacion -> notificacion.tipo().equals("PLAN_SUSPENDIDO"))
                .hasSize(1);
    }

    @Test
    void alPagarLaCuotaVencidaElPlanSeReactiva() {
        afiliarDesdeHaceDiezDias();
        Long cuotaId = cuotaService.historial(afiliado.getId()).cuotas().get(0).id();

        cuotaService.registrarPago(afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.EFECTIVO));

        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).estado()).isEqualTo("ACTIVO");
        assertThat(eventos.stream(EventoAfiliado.PlanReactivado.class)).hasSize(1);
        assertThat(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, null)).cobertura()).isEqualTo("PLAN");
    }

    // Cancelación ----------------------------------------------------------

    @Test
    void cancelarUnPlanSinActivarTerminaHoyYAnulaLaCuotaDeActivacion() {
        afiliarHoy();

        CancelacionPlanResponse simulada = cancelacionService.simular(afiliado.getId());
        assertThat(simulada.inmediata()).isTrue();
        assertThat(simulada.valorAnulado()).isEqualByComparingTo("50000");

        CancelacionPlanResponse cancelacion = cancelacionService.cancelar(afiliado.getId());

        assertThat(cancelacion.inmediata()).isTrue();
        assertThat(cancelacion.ultimoDia()).isEqualTo(hoy);
        assertThrows(AfiliacionNotFoundException.class, () -> afiliacionService.obtenerPlan(afiliado.getId()));
        assertThat(cuotaService.historial(afiliado.getId()).cuotas()).singleElement()
                .satisfies(cuota -> assertThat(cuota.estado()).isEqualTo("ANULADA"));
        assertThat(cuotaService.historial(afiliado.getId()).totalPendiente()).isZero();
        assertThat(eventos.stream(EventoAfiliado.PlanCancelado.class)).hasSize(1);
    }

    @Test
    void cancelarConElPeriodoPagadoConservaElPlanHastaElFinYSePuedeDeshacer() {
        afiliarHoy();
        pagarCuotaActual();
        LocalDate finPeriodo = hoy.plusMonths(1).minusDays(1);

        CancelacionPlanResponse cancelacion = cancelacionService.cancelar(afiliado.getId());

        assertThat(cancelacion.inmediata()).isFalse();
        assertThat(cancelacion.ultimoDia()).isEqualTo(finPeriodo);
        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).fechaFin()).isEqualTo(finPeriodo);
        assertThrows(BusinessRuleException.class, () -> cancelacionService.cancelar(afiliado.getId()));

        cancelacionService.deshacer(afiliado.getId());
        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).fechaFin()).isNull();
    }

    @Test
    void laCancelacionProgramadaTerminaElPlanAlDiaSiguienteYNoGeneraMasCuotas() {
        afiliarHoy();
        pagarCuotaActual();
        LocalDate finPeriodo = cancelacionService.cancelar(afiliado.getId()).ultimoDia();
        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoId(afiliado.getId()).orElseThrow();

        cuotaService.generarCuotas(afiliacion, finPeriodo.plusMonths(2));
        assertThat(cuotaService.historial(afiliado.getId()).cuotas()).hasSize(1);

        assertThat(cancelacionService.aplicarCancelacionesCumplidas(finPeriodo)).isZero();
        assertThat(cancelacionService.aplicarCancelacionesCumplidas(finPeriodo.plusDays(1))).isEqualTo(1);
        assertThrows(AfiliacionNotFoundException.class, () -> afiliacionService.obtenerPlan(afiliado.getId()));
    }

    @Test
    void cancelarConElPlanSuspendidoTerminaHoyYConservaLaDeuda() {
        afiliarDesdeHaceDiezDias();

        CancelacionPlanResponse cancelacion = cancelacionService.cancelar(afiliado.getId());

        assertThat(cancelacion.inmediata()).isTrue();
        assertThat(cancelacion.valorAnulado()).isZero();
        assertThat(cancelacion.deudaPendiente()).isEqualByComparingTo("50000");
        assertThat(cuotaService.historial(afiliado.getId()).totalPendiente()).isEqualByComparingTo("50000");
    }

    @Test
    void conUnaCancelacionProgramadaNoSePuedeCambiarDePlan() {
        afiliarHoy();
        pagarCuotaActual();
        cancelacionService.cancelar(afiliado.getId());
        PlanResponse otro = planService.crearPlan(new CrearPlanRequest(
                "OTR" + System.nanoTime() % 100000, "Plan Otro", new BigDecimal("70000"), 2, 3, hoy.minusDays(60)));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> afiliacionService.asignarPlan(
                afiliado.getId(), new AsignarPlanRequest(otro.id(), List.of(rocky.getId()))));
        assertThat(error.getMessage()).contains("Deshaz la cancelación");
    }

    // Qué incluye el plan ----------------------------------------------------

    @Test
    void elDetalleDelPlanIncluyeLosDescuentosPorServicio() {
        PlanResponse basico = planService.listarPlanes().stream()
                .filter(candidato -> candidato.codigo().equals("BASICO")).findFirst().orElseThrow();

        assertThat(basico.beneficios()).hasSize(5);
        assertThat(basico.beneficios()).filteredOn(beneficio -> beneficio.codigo().equals("CONSULTA_GENERAL"))
                .singleElement()
                .satisfies(beneficio -> {
                    assertThat(beneficio.porcentajeDescuento()).isEqualTo(30);
                    assertThat(beneficio.valorConPlan()).isEqualByComparingTo("42000");
                });
    }

    private void afiliarHoy() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(plan.id(), List.of(rocky.getId())));
    }

    /** Afiliación que empezó hace 10 días con la cuota sin pagar: el plazo venció hace 6. */
    private void afiliarDesdeHaceDiezDias() {
        Afiliacion afiliacion = afiliacionRepository.saveAndFlush(new Afiliacion(
                afiliado, planRepository.findById(plan.id()).orElseThrow(), hoy.minusDays(10), List.of(rocky)));
        cuotaService.generarCuotas(afiliacion, hoy);
    }

    private void pagarCuotaActual() {
        Long cuotaId = cuotaService.historial(afiliado.getId()).cuotas().get(0).id();
        cuotaService.registrarPago(afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.TARJETA));
    }

    private Long consultaGeneral() {
        return servicioRepository.findAll().stream()
                .filter(servicio -> servicio.getCodigo().equals("CONSULTA_GENERAL"))
                .findFirst().orElseThrow().getId();
    }
}
