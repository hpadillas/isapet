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
import com.isapet.entity.MedioPago;
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
import com.isapet.service.CuotaPlanService;
import com.isapet.service.EventoAfiliado;
import com.isapet.service.PrestacionService;
import com.isapet.service.SimulacionPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
@RecordApplicationEvents
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
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private ApplicationEvents eventos;

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
        // 60.000 de mensualidad; la consulta es una de las 2 atenciones incluidas.
        assertThat(basico.valorMensualEstimado()).isEqualByComparingTo("60000");
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
    void simulacionCobraConBonoLasAtencionesQueSuperanLasIncluidas() {
        SimulacionPlanResponse resultado = simulacionService.simular(new SimulacionPlanRequest(
                1, List.of(new ServicioEstimadoRequest(servicio("CONSULTA_GENERAL").getId(), 3))));

        // Básico incluye 2 atenciones; la tercera se paga con un bono de 42.000 (30 % de descuento).
        assertThat(opcion(resultado, "BASICO").costoServiciosConPlan()).isEqualByComparingTo("42000");
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
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());

        assertThat(plan.codigo()).isEqualTo("ESTANDAR");
        assertThat(plan.precioMensual()).isEqualByComparingTo("100000");
        assertThat(plan.mascotasCubiertas()).extracting("nombre").containsExactly("Rocky", "Luna");
        assertThat(plan.atencionesIncluidasRestantes()).isEqualTo(4);
        BeneficioServicioResponse consulta = plan.beneficios().stream()
                .filter(beneficio -> beneficio.codigo().equals("CONSULTA_GENERAL"))
                .findFirst().orElseThrow();
        assertThat(consulta.porcentajeDescuento()).isEqualTo(45);
        assertThat(consulta.valorConPlan()).isEqualByComparingTo("33000");
    }

    @Test
    void noPermiteCubrirMasMascotasQueLasDelPlan() {
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                        plan("BASICO").getId(), List.of(rocky.getId(), luna.getId()))));

        assertThat(error.getMessage()).contains("máximo 1");
    }

    @Test
    void noPermiteCubrirMascotasDeOtroAfiliado() {
        Afiliado otro = afiliadoRepository.saveAndFlush(new Afiliado("IT2-" + System.nanoTime(), "Luis", "Pérez"));
        Mascota ajena = mascotaRepository.saveAndFlush(new Mascota("Max", "Perro", otro));

        assertThrows(BusinessRuleException.class, () -> asignarYActivar(
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
    void comprarBonoAplicaElDescuentoDelPlanCuandoSeAgotanLasIncluidas() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        agotarAtencionesIncluidas(rocky, 2);
        Long consulta = servicio("CONSULTA_GENERAL").getId();

        CotizacionBonoResponse cotizacion = bonoService.cotizar(afiliado.getId(), rocky.getId(), consulta);
        BonoResponse bono = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));

        // Consulta general 60.000 con 30 % de descuento Básico.
        assertThat(cotizacion.valorAPagar()).isEqualByComparingTo("42000");
        assertThat(bono.valorPagado()).isEqualByComparingTo("42000");
        assertThat(bono.estado()).isEqualTo("DISPONIBLE");
        assertThat(bono.codigo()).startsWith("BN-");
        // Después de agotar las incluidas, los bonos no tienen tope.
        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));
        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));
        assertThat(bonoService.listar(afiliado.getId())).hasSize(3);
    }

    @Test
    void noVendeBonosSinPlanNiParaMascotasNoCubiertas() {
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO)));

        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(luna.getId(), consulta, MedioPago.EFECTIVO)));
        assertThat(error.getMessage()).contains("no está cubierta");
    }

    @Test
    void noVendeBonosMientrasQuedenAtencionesIncluidas() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        Long vacuna = servicio("VACUNACION").getId();

        BusinessRuleException sinUsar = assertThrows(BusinessRuleException.class, () ->
                bonoService.cotizar(afiliado.getId(), rocky.getId(), vacuna));
        assertThat(sinUsar.getMessage()).contains("quedan 2 de 2");

        agotarAtencionesIncluidas(rocky, 1);
        BusinessRuleException unaUsada = assertThrows(BusinessRuleException.class, () ->
                bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), vacuna, MedioPago.EFECTIVO)));
        assertThat(unaUsada.getMessage()).contains("quedan 1 de 2");

        agotarAtencionesIncluidas(rocky, 1);
        assertThat(bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), vacuna, MedioPago.EFECTIVO)).id()).isNotNull();
    }

    @Test
    void unPlanConAtencionesIlimitadasNoNecesitaBonos() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("PREMIUM").getId(), List.of(rocky.getId())));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> bonoService.comprar(
                afiliado.getId(), new CompraBonoRequest(rocky.getId(), servicio("CIRUGIA").getId(), MedioPago.EFECTIVO)));
        assertThat(error.getMessage()).contains("ilimitadas");
    }

    @Test
    void elRechazoPorAtencionesDisponiblesIndicaCuantasQuedanYHastaCuando() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        agotarAtencionesIncluidas(rocky, 1);

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                bonoService.cotizar(afiliado.getId(), rocky.getId(), servicio("CONSULTA_GENERAL").getId()));

        assertThat(error.getMessage()).isEqualTo(
                "Al afiliado le quedan 1 de 2 atenciones incluidas en su plan en el periodo actual (hasta el %s); "
                        .formatted(LocalDate.now().plusMonths(1).minusDays(1))
                        + "los bonos se compran cuando se agoten.");
    }

    // F-06 ---------------------------------------------------------------

    @Test
    void lasAtencionesAnterioresALaAfiliacionSonParticularesYNoConsumenElCupo() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));

        String cobertura = prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), servicio("CONSULTA_GENERAL").getId(), LocalDate.now().minusDays(1), "VetCasa", null, null))
                .cobertura();

        assertThat(cobertura).isEqualTo("PARTICULAR");
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () ->
                bonoService.cotizar(afiliado.getId(), rocky.getId(), servicio("CONSULTA_GENERAL").getId()));
        assertThat(error.getMessage()).contains("quedan 2 de 2");
    }

    @Test
    void conAtencionesIlimitadasTodasSonDelPlanYNuncaSeAvisaCupoAgotado() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("PREMIUM").getId(), List.of(rocky.getId())));
        Long consulta = servicio("CONSULTA_GENERAL").getId();

        List<String> coberturas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            coberturas.add(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                    rocky.getId(), consulta, LocalDate.now(), "VetCasa", null, null)).cobertura());
        }

        assertThat(coberturas).containsOnly("PLAN").hasSize(5);
        assertThat(eventos.stream(EventoAfiliado.AtencionesIncluidasAgotadas.class)).isEmpty();
    }

    @Test
    void lasAtencionesSinBonoUsanLasIncluidasYDespuesSonParticulares() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        Long consulta = servicio("CONSULTA_GENERAL").getId();

        List<String> coberturas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            coberturas.add(prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                    rocky.getId(), consulta, LocalDate.now(), "VetCasa", null, null)).cobertura());
        }
        String lunaNoCubierta = prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                luna.getId(), consulta, LocalDate.now(), "VetCasa", null, null)).cobertura();

        assertThat(coberturas).containsExactly("PLAN", "PLAN", "PARTICULAR");
        assertThat(lunaNoCubierta).isEqualTo("PARTICULAR");
        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());
        assertThat(plan.atencionesIncluidasUsadas()).isEqualTo(2);
        assertThat(plan.atencionesIncluidasRestantes()).isZero();
        assertThat(plan.atencionesIncluidasPeriodo()).hasSize(2)
                .allSatisfy(prestacion -> assertThat(prestacion.mascotaNombre()).isEqualTo("Rocky"));
        assertThat(plan.periodoDesde()).isEqualTo(LocalDate.now());
        assertThat(plan.periodoHasta()).isEqualTo(LocalDate.now().plusMonths(1).minusDays(1));
        // Solo la atención que agota el cupo genera el aviso, una vez.
        assertThat(eventos.stream(EventoAfiliado.AtencionesIncluidasAgotadas.class))
                .containsExactly(new EventoAfiliado.AtencionesIncluidasAgotadas(afiliado.getId(), LocalDate.now()));
    }

    @Test
    void soloLaActivacionDeUnPlanPublicaElAvisoDePlanActivado() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        // Cambiar solo las mascotas cubiertas no es una nueva activación.
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(luna.getId())));
        // Cambiar a otro plan lo programa; el aviso sale cuando se activa.
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));
        assertThat(eventos.stream(EventoAfiliado.PlanActivado.class)).hasSize(1);

        afiliacionService.aplicarCambiosProgramados(LocalDate.now().plusMonths(1));
        assertThat(eventos.stream(EventoAfiliado.PlanActivado.class)).hasSize(2);
    }

    @Test
    void registrarPrestacionConBonoLoMarcaComoUsadoYApareceEnElHistorial() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        agotarAtencionesIncluidas(rocky, 2);
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        BonoResponse bono = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));

        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consulta, LocalDate.now(), "Clínica Veterinaria Central", "Control anual", bono.id()));
        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                luna.getId(), servicio("VACUNACION").getId(), LocalDate.now().minusDays(10), "VetCasa", null, null));

        List<PrestacionResponse> historial = prestacionService.listar(afiliado.getId(), null);
        assertThat(historial).hasSize(4);
        assertThat(historial.get(0).mascotaNombre()).isEqualTo("Rocky");
        assertThat(historial.get(0).bonoCodigo()).isEqualTo(bono.codigo());
        assertThat(historial.get(0).cobertura()).isEqualTo("BONO");
        assertThat(prestacionService.listar(afiliado.getId(), luna.getId())).hasSize(1);
        assertThat(bonoService.listar(afiliado.getId()).get(0).estado()).isEqualTo("USADO");

        BusinessRuleException reutilizado = assertThrows(BusinessRuleException.class, () ->
                prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                        rocky.getId(), consulta, LocalDate.now(), "Otra clínica", null, bono.id())));
        assertThat(reutilizado.getMessage()).contains("ya fue utilizado");
    }

    @Test
    void elPlanMuestraLasAtencionesIncluidasYLosBonosDelPeriodo() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("BASICO").getId(), List.of(rocky.getId())));
        agotarAtencionesIncluidas(rocky, 2);
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        BonoResponse usado = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));
        BonoResponse sinUsar = bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consulta, MedioPago.EFECTIVO));
        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consulta, LocalDate.now(), "VetCasa", null, usado.id()));

        PlanAfiliadoResponse plan = afiliacionService.obtenerPlan(afiliado.getId());

        assertThat(plan.atencionesIncluidasUsadas()).isEqualTo(2);
        assertThat(plan.atencionesIncluidasRestantes()).isZero();
        assertThat(plan.bonosPeriodo()).extracting(BonoResponse::id, BonoResponse::estado)
                .containsExactlyInAnyOrder(
                        tuple(usado.id(), "USADO"),
                        tuple(sinUsar.id(), "DISPONIBLE"));
    }

    @Test
    void noAceptaUnBonoDeOtroServicioOMascota() {
        asignarYActivar(afiliado.getId(), new AsignarPlanRequest(
                plan("ESTANDAR").getId(), List.of(rocky.getId(), luna.getId())));
        agotarAtencionesIncluidas(rocky, 4);
        BonoResponse bono = bonoService.comprar(afiliado.getId(),
                new CompraBonoRequest(rocky.getId(), servicio("CONSULTA_GENERAL").getId(), MedioPago.EFECTIVO));

        assertThrows(BusinessRuleException.class, () -> prestacionService.registrar(afiliado.getId(),
                new RegistrarPrestacionRequest(luna.getId(), servicio("CONSULTA_GENERAL").getId(),
                        LocalDate.now(), "VetCasa", null, bono.id())));
        assertThrows(BusinessRuleException.class, () -> prestacionService.registrar(afiliado.getId(),
                new RegistrarPrestacionRequest(rocky.getId(), servicio("VACUNACION").getId(),
                        LocalDate.now(), "VetCasa", null, bono.id())));
    }

    /** Registra atenciones sin bono que consumen las incluidas en el plan del mes. */
    private void agotarAtencionesIncluidas(Mascota mascota, int cantidad) {
        Long consulta = servicio("CONSULTA_GENERAL").getId();
        for (int i = 0; i < cantidad; i++) {
            PrestacionResponse prestacion = prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                    mascota.getId(), consulta, LocalDate.now(), "VetCasa", null, null));
            assertThat(prestacion.cobertura()).isEqualTo("PLAN");
        }
    }

    /** Asigna el plan y paga la cuota de activación, para probar con el plan ya activo. */
    private PlanAfiliadoResponse asignarYActivar(Long afiliadoId, AsignarPlanRequest request) {
        afiliacionService.asignarPlan(afiliadoId, request);
        ActivacionPlan.pagarActivacion(cuotaService, afiliadoId);
        return afiliacionService.obtenerPlan(afiliadoId);
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
