package com.isapet.integration;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.BeneficioServicioResponse;
import com.isapet.dto.BonoResponse;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CotizacionBonoResponse;
import com.isapet.dto.OpcionPlanSimuladaResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.PrestacionResponse;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.dto.ServicioEstimadoRequest;
import com.isapet.dto.SimulacionPlanRequest;
import com.isapet.dto.SimulacionPlanResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.Plan;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.exception.AfiliacionNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.PrestacionService;
import com.isapet.service.SimulacionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre F-03 a F-06 contra PostgreSQL con los planes (V3) y servicios/beneficios (V5) sembrados.
 * Cada prueba se revierte al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AfiliacionBonosPrestacionesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private ServicioVeterinarioRepository servicioRepository;
    @Autowired private SimulacionPlanService simulacionService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private BonoService bonoService;
    @Autowired private PrestacionService prestacionService;

    private Afiliado afiliado;
    private Mascota rocky;
    private Mascota luna;

    @BeforeEach
    void prepararAfiliado() {
        afiliado = afiliadoRepository.saveAndFlush(
                new Afiliado("IT-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana@example.com", "Calle 1"));
        rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        luna = mascotaRepository.saveAndFlush(new Mascota("Luna", "Gato", "Criollo", 2, "Gris", afiliado));
    }

    // F-03 ---------------------------------------------------------------

    @Test
    void simulacionRecomiendaElPlanMasEconomicoQueCubreLasMascotas() {
        SimulacionPlanResponse resultado = simulacionService.simular(new SimulacionPlanRequest(
                1, List.of(new ServicioEstimadoRequest(servicio("CONSULTA_GENERAL").getId(), 1))));

        OpcionPlanSimuladaResponse basico = opcion(resultado, "BASICO");
        // 60.000 de mensualidad + consulta general con 30 % de descuento (42.000).
        assertThat(basico.valorMensualEstimado()).isEqualByComparingTo("102000");
        assertThat(basico.recomendado()).isTrue();
        assertThat(opcion(resultado, "PREMIUM").recomendado()).isFalse();
    }

    @Test
    void simulacionMarcaPlanesQueNoCubrenLaCantidadDeMascotas() {
        SimulacionPlanResponse resultado = simulacionService.simular(new SimulacionPlanRequest(3, List.of()));

        assertThat(opcion(resultado, "BASICO").aplica()).isFalse();
        assertThat(opcion(resultado, "BASICO").motivoNoAplica()).contains("máximo 1");
        assertThat(opcion(resultado, "ESTANDAR").aplica()).isFalse();
        assertThat(opcion(resultado, "PREMIUM").aplica()).isTrue();
        assertThat(opcion(resultado, "PREMIUM").recomendado()).isTrue();
    }

    @Test
    void simulacionCobraAValorPlenoLasAtencionesQueSuperanElLimite() {
        SimulacionPlanResponse resultado = simulacionService.simular(new SimulacionPlanRequest(
                1, List.of(new ServicioEstimadoRequest(servicio("CONSULTA_GENERAL").getId(), 3))));

        // Básico permite 2 atenciones: 2 x 42.000 con descuento + 1 x 60.000 a valor pleno.
        assertThat(opcion(resultado, "BASICO").costoServiciosConPlan()).isEqualByComparingTo("144000");
        assertThat(opcion(resultado, "BASICO").costoServiciosSinPlan()).isEqualByComparingTo("180000");
    }

    @Test
    void simulacionEsPublicaYNoRequiereCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/simulaciones/planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidadMascotas\":2,\"servicios\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadMascotas").value(2));
        mockMvc.perform(get("/api/v1/simulaciones/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void simulacionRechazaDatosInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/simulaciones/planes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidadMascotas\":0}"))
                .andExpect(status().isBadRequest());
    }

    // F-04 ---------------------------------------------------------------

    @Test
    void consultaElPlanAsignadoConBeneficiosYMascotasCubiertas() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());

        assertThat(plan.codigo()).isEqualTo("ESTANDAR");
        assertThat(plan.precioMensual()).isEqualByComparingTo("100000");
        assertThat(plan.mascotasCubiertas()).extracting("nombre").containsExactly("Rocky", "Luna");
        assertThat(plan.bonosDisponiblesMes()).isEqualTo(4);
        BeneficioServicioResponse consulta = plan.beneficios().stream()
                .filter(beneficio -> beneficio.codigo().equals("CONSULTA_GENERAL"))
                .findFirst().orElseThrow();
        assertThat(consulta.porcentajeDescuento()).isEqualTo(45);
        assertThat(consulta.valorConPlan()).isEqualByComparingTo("33000");
    }

    @Test
    void noPermiteCubrirMasMascotasQueLasDelPlan() {
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                        plan("BASICO").getId(), List.of(rocky.getId(), luna.getId()))));

        assertThat(error.getMessage()).contains("máximo 1");
    }

    @Test
    void noPermiteCubrirMascotasDeOtroAfiliado() {
        Afiliado otro = afiliadoRepository.saveAndFlush(new Afiliado("IT2-" + System.nanoTime(), "Luis", "Pérez"));
        Mascota ajena = mascotaRepository.saveAndFlush(new Mascota("Max", "Perro", otro));

        assertThrows(BusinessRuleException.class, () -> afiliacionService.asignarPlan(
                afiliado.getId(), new AsignarPlanRequest(plan("PREMIUM").getId(), List.of(ajena.getId()))));
    }

    @Test
    void consultarPlanSinAfiliacionResponde404() throws Exception {
        assertThrows(AfiliacionNotFoundException.class, () -> afiliacionService.obtenerPlan(afiliado.getId()));
    }

    @Test
    void elPlanDelAfiliadoRequiereSesionAdministrativa() throws Exception {
        mockMvc.perform(get("/api/v1/afiliados/{id}/plan", afiliado.getId()))
                .andExpect(status().isUnauthorized());
    }

    // F-05 ---------------------------------------------------------------

    @Test
    void comprarBonoAplicaElDescuentoDelPlan() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("PREMIUM").getId(), List.of(rocky.getId())));
        Long cirugia = servicio("CIRUGIA").getId();

        CotizacionBonoResponse cotizacion = bonoService.cotizar(afiliado.getId(), rocky.getId(), cirugia);
        BonoResponse bono = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), cirugia));

        // Cirugía 750.000 con 25 % de descuento Premium.
        assertThat(cotizacion.valorAPagar()).isEqualByComparingTo("562500");
        assertThat(bono.valorPagado()).isEqualByComparingTo("562500");
        assertThat(bono.estado()).isEqualTo("DISPONIBLE");
        assertThat(bono.codigo()).startsWith("BN-");
        assertThat(bonoService.listar(afiliado.getId())).hasSize(1);
    }

    @Test
    void noVendeBonosSinPlanNiParaMascotasNoCubiertas() {
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta)));

        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(luna.getId(), consulta)));
        assertThat(error.getMessage()).contains("no está cubierta");
    }

    @Test
    void respetaElLimiteMensualDeAtencionesDelPlan() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        Long vacuna = servicio("VACUNACION").getId();

        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), vacuna));
        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), vacuna));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), vacuna)));
        assertThat(error.getMessage()).contains("límite de 2");
        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).bonosDisponiblesMes()).isZero();
    }

    // F-06 ---------------------------------------------------------------

    @Test
    void registrarPrestacionConBonoLoMarcaComoUsadoYApareceEnElHistorial() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        BonoResponse bono = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta));

        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consulta, LocalDate.now(), "Clínica Veterinaria Central", "Control anual", bono.id()));
        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                luna.getId(), servicio("VACUNACION").getId(), LocalDate.now().minusDays(10), "VetCasa", null, null));

        List<PrestacionResponse> historial = prestacionService.listar(afiliado.getId(), null);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).mascotaNombre()).isEqualTo("Rocky");
        assertThat(historial.get(0).bonoCodigo()).isEqualTo(bono.codigo());
        assertThat(prestacionService.listar(afiliado.getId(), luna.getId())).hasSize(1);
        assertThat(bonoService.listar(afiliado.getId()).get(0).estado()).isEqualTo("USADO");

        BusinessRuleException reutilizado = assertThrows(BusinessRuleException.class, () ->
                prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                        rocky.getId(), consulta, LocalDate.now(), "Otra clínica", null, bono.id())));
        assertThat(reutilizado.getMessage()).contains("ya fue utilizado");
    }

    @Test
    void noAceptaUnBonoDeOtroServicioOMascota() {
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));
        BonoResponse bono = bonoService.comprar(afiliado.getId(),
                new CompraBonoRequest(rocky.getId(), servicio("CONSULTA_GENERAL").getId()));

        assertThrows(BusinessRuleException.class, () -> prestacionService.registrar(afiliado.getId(),
                new RegistrarPrestacionRequest(luna.getId(), servicio("CONSULTA_GENERAL").getId(),
                        LocalDate.now(), "VetCasa", null, bono.id())));
        assertThrows(BusinessRuleException.class, () -> prestacionService.registrar(afiliado.getId(),
                new RegistrarPrestacionRequest(rocky.getId(), servicio("VACUNACION").getId(),
                        LocalDate.now(), "VetCasa", null, bono.id())));
    }

    private Plan plan(String codigo) {
        return planRepository.findAllByOrderByCodigoAsc().stream()
                .filter(plan -> plan.getCodigo().equalsIgnoreCase(codigo))
                .findFirst().orElseThrow();
    }

    private ServicioVeterinario servicio(String codigo) {
        return servicioRepository.findAll().stream()
                .filter(servicio -> servicio.getCodigo().equals(codigo))
                .findFirst().orElseThrow();
    }

    private static OpcionPlanSimuladaResponse opcion(SimulacionPlanResponse resultado, String codigo) {
        return resultado.opciones().stream()
                .filter(opcion -> opcion.codigo().equalsIgnoreCase(codigo))
                .findFirst().orElseThrow();
    }
}
