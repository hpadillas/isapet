package com.isapet.integration;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.PlanResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.dto.ResumenInicioResponse;
import com.isapet.dto.ResumenInicioResponse.Alerta;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.MedioPago;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.CancelacionPlanService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.PlanService;
import com.isapet.service.ResumenInicioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Panel de inicio: cifras, alertas y últimos afiliados. Cada prueba se revierte. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResumenInicioIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ResumenInicioService resumenService;
    @Autowired private PlanService planService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private CancelacionPlanService cancelacionService;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private AfiliacionRepository afiliacionRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;

    private final LocalDate hoy = LocalDate.now();
    private PlanResponse plan;

    @BeforeEach
    void crearPlan() {
        plan = planService.crearPlan(new CrearPlanRequest(
                "RES" + System.nanoTime() % 100000, "Plan Resumen", new BigDecimal("50000"), 2, 2, hoy.minusDays(60)));
    }

    @Test
    void reportaCuotasPorVencerSuspensionesYCancelaciones() {
        Afiliado alDia = afiliado("Ana");
        afiliacionService.asignarPlan(alDia.getId(), new AsignarPlanRequest(plan.id(), List.of(mascota(alDia).getId())));

        Afiliado moroso = afiliado("Luis");
        Afiliacion vencida = afiliacionRepository.saveAndFlush(new Afiliacion(
                moroso, planRepository.findById(plan.id()).orElseThrow(), hoy.minusDays(10), List.of(mascota(moroso))));
        cuotaService.generarCuotas(vencida, hoy);

        Afiliado cancela = afiliado("Eva");
        afiliacionService.asignarPlan(cancela.getId(), new AsignarPlanRequest(plan.id(), List.of(mascota(cancela).getId())));
        Long cuota = cuotaService.historial(cancela.getId()).cuotas().get(0).id();
        cuotaService.registrarPago(cancela.getId(), cuota, new RegistrarPagoRequest(hoy, MedioPago.EFECTIVO));
        cancelacionService.cancelar(cancela.getId());

        ResumenInicioResponse resumen = resumenService.resumen();

        assertThat(resumen.afiliadosSuspendidos()).isGreaterThanOrEqualTo(1);
        assertThat(alertasDe(resumen, moroso)).extracting(Alerta::tipo).containsExactly("SUSPENDIDO");
        // Recién asignado: su plan espera el pago de la primera cuota para activarse.
        assertThat(alertasDe(resumen, alDia)).singleElement().satisfies(alerta -> {
            assertThat(alerta.tipo()).isEqualTo("PENDIENTE_ACTIVACION");
            assertThat(alerta.mensaje()).contains("se activa al pagar");
        });
        assertThat(alertasDe(resumen, cancela)).extracting(Alerta::tipo).containsExactly("CANCELACION_PROGRAMADA");
        // Las suspensiones van primero.
        assertThat(resumen.alertas().get(0).tipo()).isEqualTo("SUSPENDIDO");
        assertThat(resumen.ultimosAfiliados()).extracting(ResumenInicioResponse.AfiliadoReciente::id)
                .startsWith(cancela.getId(), moroso.getId(), alDia.getId());
    }

    @Test
    void elResumenRequiereSesionAdministrativa() throws Exception {
        mockMvc.perform(get("/api/v1/admin/resumen")).andExpect(status().isUnauthorized());
    }

    private List<Alerta> alertasDe(ResumenInicioResponse resumen, Afiliado afiliado) {
        return resumen.alertas().stream().filter(alerta -> alerta.afiliadoId().equals(afiliado.getId())).toList();
    }

    private Afiliado afiliado(String nombre) {
        return afiliadoRepository.saveAndFlush(new Afiliado(
                "RS-" + System.nanoTime(), nombre, "Prueba", "3001234567", "prueba@example.com", "Calle 1"));
    }

    private Mascota mascota(Afiliado afiliado) {
        return mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
    }
}
