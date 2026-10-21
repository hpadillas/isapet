package com.isapet.integration;

import com.isapet.dto.ActualizarPlanRequest;
import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.CrearVersionPlanRequest;
import com.isapet.dto.PlanResponse;
import com.isapet.dto.VersionPlanResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.exception.PlanVersionNotFoundException;
import com.isapet.exception.VersionPlanConflictException;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Edición de un plan: nombre inmediato y condiciones comerciales como versiones con vigencia. */
@SpringBootTest
@Transactional
class EdicionPlanIntegrationTest {

    @Autowired private PlanService planService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private JdbcTemplate jdbc;

    private final LocalDate hoy = LocalDate.now();

    @Test
    void editarCondicionesPublicaUnaVersionYCierraLaVigenciaDeLaAnterior() {
        PlanResponse plan = crearPlan();

        PlanResponse editado = planService.publicarVersion(plan.id(), new CrearVersionPlanRequest(
                new BigDecimal("90000"), 2, 3, hoy.plusDays(10)));

        assertThat(editado.versiones())
                .extracting(VersionPlanResponse::precioMensual, VersionPlanResponse::vigenteDesde,
                        VersionPlanResponse::vigenteHastaExclusiva)
                .containsExactly(
                        tuple(new BigDecimal("80000"), hoy, hoy.plusDays(10)),
                        tuple(new BigDecimal("90000"), hoy.plusDays(10), null));
        // El detalle consultado después conserva las mismas vigencias.
        assertThat(planService.obtenerPlan(plan.id()).versiones()).hasSize(2);
    }

    @Test
    void unaVersionNuevaDebeEmpezarDespuesDeLaUltima() {
        PlanResponse plan = crearPlan();

        assertThrows(VersionPlanConflictException.class, () -> planService.publicarVersion(plan.id(),
                new CrearVersionPlanRequest(new BigDecimal("90000"), 2, 3, hoy)));
    }

    @Test
    void cambiarElNombreNoCreaUnaVersion() {
        PlanResponse plan = crearPlan();

        PlanResponse renombrado = planService.actualizarPlan(plan.id(), new ActualizarPlanRequest("  Plan Oro Plus  "));

        assertThat(renombrado.nombre()).isEqualTo("Plan Oro Plus");
        assertThat(renombrado.codigo()).isEqualTo(plan.codigo());
        assertThat(renombrado.versiones()).hasSize(1);
    }

    @Test
    void eliminarLaVersionProgramadaDelMedioExtiendeLaVigenteHastaLaSiguiente() {
        PlanResponse plan = crearPlan();
        planService.publicarVersion(plan.id(), new CrearVersionPlanRequest(new BigDecimal("90000"), 2, 3, hoy.plusDays(10)));
        PlanResponse conTres = planService.publicarVersion(plan.id(),
                new CrearVersionPlanRequest(new BigDecimal("95000"), 3, 4, hoy.plusDays(20)));
        Long segunda = conTres.versiones().get(1).id();

        PlanResponse resultado = planService.eliminarVersion(plan.id(), segunda);

        // La primera (vigente) sigue hasta que empiece la tercera.
        assertThat(resultado.versiones())
                .extracting(VersionPlanResponse::precioMensual, VersionPlanResponse::vigenteDesde,
                        VersionPlanResponse::vigenteHastaExclusiva)
                .containsExactly(
                        tuple(new BigDecimal("80000"), hoy, hoy.plusDays(20)),
                        tuple(new BigDecimal("95000"), hoy.plusDays(20), null));
        assertThat(planService.obtenerVersionAplicable(plan.id(), hoy.plusDays(15)).orElseThrow().precioMensual())
                .isEqualByComparingTo("80000");
    }

    @Test
    void eliminarLaUltimaVersionProgramadaDejaLaVigenteSinFechaDeFin() {
        PlanResponse plan = crearPlan();
        PlanResponse conDos = planService.publicarVersion(plan.id(),
                new CrearVersionPlanRequest(new BigDecimal("90000"), 2, 3, hoy.plusDays(10)));

        PlanResponse resultado = planService.eliminarVersion(plan.id(), conDos.versiones().get(1).id());

        assertThat(resultado.versiones()).singleElement()
                .satisfies(version -> assertThat(version.vigenteHastaExclusiva()).isNull());
    }

    @Test
    void noSeEliminanVersionesVigentesNiLaUnicaDelPlan() {
        PlanResponse vigente = crearPlan();
        VersionPlanConflictException yaEmpezo = assertThrows(VersionPlanConflictException.class,
                () -> planService.eliminarVersion(vigente.id(), vigente.versiones().get(0).id()));
        assertThat(yaEmpezo.getMessage()).contains("Solo se pueden eliminar versiones programadas");

        PlanResponse futuro = planService.crearPlan(new CrearPlanRequest(
                "FUT" + System.nanoTime() % 100000, "Plan Futuro", new BigDecimal("80000"), 2, 3, hoy.plusDays(5)));
        VersionPlanConflictException unica = assertThrows(VersionPlanConflictException.class,
                () -> planService.eliminarVersion(futuro.id(), futuro.versiones().get(0).id()));
        assertThat(unica.getMessage()).contains("única versión");

        assertThrows(PlanVersionNotFoundException.class, () -> planService.eliminarVersion(vigente.id(), -1L));
    }

    @Test
    void elImpactoCuentaLosCambiosDePlanProgramadosDuranteLaVersion() {
        PlanResponse plan = crearPlan();
        PlanResponse conDos = planService.publicarVersion(plan.id(),
                new CrearVersionPlanRequest(new BigDecimal("90000"), 2, 3, hoy.plusDays(10)));
        Long programada = conDos.versiones().get(1).id();
        assertThat(planService.impactoEliminacionVersion(plan.id(), programada).afiliadosConCambioProgramado()).isZero();

        // Un afiliado con Básico programa el cambio a este plan: se activa en un mes, ya con la versión programada.
        Afiliado afiliado = afiliadoRepository.saveAndFlush(new Afiliado(
                "EP-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana@example.com", "Calle 1"));
        Mascota rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        Long basico = planRepository.findAllByOrderByCodigoAsc().stream()
                .filter(candidato -> candidato.getCodigo().equals("BASICO")).findFirst().orElseThrow().getId();
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(basico, List.of(rocky.getId())));
        ActivacionPlan.pagarActivacion(cuotaService, afiliado.getId());
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(plan.id(), List.of(rocky.getId())));

        assertThat(planService.impactoEliminacionVersion(plan.id(), programada).afiliadosConCambioProgramado()).isEqualTo(1);
    }

    @Test
    void laBaseDeDatosImpideBorrarUnaVersionVigenteAunqueSeSalteElServicio() {
        Long vigente = crearPlan().versiones().get(0).id();

        assertThrows(DataAccessException.class, () -> jdbc.update("delete from version_plan where id = ?", vigente));
    }

    private PlanResponse crearPlan() {
        return planService.crearPlan(new CrearPlanRequest(
                "ORO" + System.nanoTime() % 100000, "Plan Oro", new BigDecimal("80000"), 2, 3, hoy));
    }
}
