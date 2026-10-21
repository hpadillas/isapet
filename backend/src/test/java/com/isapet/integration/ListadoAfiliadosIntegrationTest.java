package com.isapet.integration;

import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.EstadoPlanAfiliado;
import com.isapet.dto.PlanResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.AfiliadoService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Listado de afiliados con el estado de su plan y el filtro por estado. Cada prueba se revierte. */
@SpringBootTest
@Transactional
class ListadoAfiliadosIntegrationTest {

    @Autowired private AfiliadoService afiliadoService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private PlanService planService;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private AfiliacionRepository afiliacionRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;

    private final LocalDate hoy = LocalDate.now();
    /** Apellido único para que la búsqueda solo traiga los afiliados de esta prueba. */
    private final String apellido = "Listado" + System.nanoTime();

    @BeforeEach
    void prepararCuatroEstados() {
        PlanResponse plan = planService.crearPlan(new CrearPlanRequest(
                "LST" + System.nanoTime() % 100000, "Plan Lista", new BigDecimal("50000"), 2, 2, hoy.minusDays(60)));

        afiliado("Sinplan");

        Afiliado porActivar = afiliado("Poractivar");
        afiliacionService.asignarPlan(porActivar.getId(), new AsignarPlanRequest(plan.id(), List.of(mascota(porActivar).getId())));

        Afiliado activo = afiliado("Activo");
        afiliacionService.asignarPlan(activo.getId(), new AsignarPlanRequest(plan.id(), List.of(mascota(activo).getId())));
        ActivacionPlan.pagarActivacion(cuotaService, activo.getId());

        // Afiliación activa desde hace 10 días con su cuota sin pagar: el plazo venció.
        Afiliado suspendido = afiliado("Suspendido");
        Afiliacion vencida = afiliacionRepository.saveAndFlush(new Afiliacion(
                suspendido, planRepository.findById(plan.id()).orElseThrow(), hoy.minusDays(10), List.of(mascota(suspendido))));
        cuotaService.generarCuotas(vencida, hoy);
    }

    @Test
    void elListadoMuestraElPlanYSuEstado() {
        List<AfiliadoListaResponse> todos = listar(null);

        assertThat(todos)
                .extracting(AfiliadoListaResponse::nombres, AfiliadoListaResponse::plan, AfiliadoListaResponse::estadoPlan)
                .containsExactlyInAnyOrder(
                        tuple("Sinplan", null, "SIN_PLAN"),
                        tuple("Poractivar", "Plan Lista", "POR_ACTIVAR"),
                        tuple("Activo", "Plan Lista", "ACTIVO"),
                        tuple("Suspendido", "Plan Lista", "SUSPENDIDO"));
    }

    @Test
    void sePuedeFiltrarPorCadaEstado() {
        assertThat(listar(EstadoPlanAfiliado.SIN_PLAN)).extracting(AfiliadoListaResponse::nombres).containsExactly("Sinplan");
        assertThat(listar(EstadoPlanAfiliado.POR_ACTIVAR)).extracting(AfiliadoListaResponse::nombres).containsExactly("Poractivar");
        assertThat(listar(EstadoPlanAfiliado.ACTIVO)).extracting(AfiliadoListaResponse::nombres).containsExactly("Activo");
        assertThat(listar(EstadoPlanAfiliado.SUSPENDIDO)).extracting(AfiliadoListaResponse::nombres).containsExactly("Suspendido");
    }

    private List<AfiliadoListaResponse> listar(EstadoPlanAfiliado estado) {
        return afiliadoService.listar(apellido, estado, PageRequest.of(0, 10)).getContent();
    }

    private Afiliado afiliado(String nombre) {
        return afiliadoRepository.saveAndFlush(new Afiliado(
                "LA-" + System.nanoTime(), nombre, apellido, "3001234567", "lista@example.com", "Calle 1"));
    }

    private Mascota mascota(Afiliado afiliado) {
        return mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
    }
}
