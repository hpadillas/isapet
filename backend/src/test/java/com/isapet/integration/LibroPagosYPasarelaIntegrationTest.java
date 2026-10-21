package com.isapet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.CompraBonoEnLineaRequest;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.ConciliacionPagosResponse;
import com.isapet.dto.CrearPlanRequest;
import com.isapet.dto.EventoPasarelaRequest;
import com.isapet.dto.PagoEnLineaResponse;
import com.isapet.dto.PagoResponse;
import com.isapet.dto.PlanResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.entity.Afiliado;
import com.isapet.entity.EstadoPago;
import com.isapet.entity.Mascota;
import com.isapet.entity.MedioPago;
import com.isapet.exception.BusinessRuleException;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.BonoService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.FirmaPasarela;
import com.isapet.service.PagoService;
import com.isapet.service.PlanService;
import com.isapet.service.PrestacionService;
import com.isapet.service.WebhookPasarelaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Libro de pagos, pagos en línea con la pasarela (webhook firmado) y conciliación. Cada prueba se revierte. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibroPagosYPasarelaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private ServicioVeterinarioRepository servicioRepository;
    @Autowired private PlanService planService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private BonoService bonoService;
    @Autowired private PrestacionService prestacionService;
    @Autowired private PagoService pagoService;
    @Autowired private WebhookPasarelaService webhookService;
    @Autowired private FirmaPasarela firma;

    private final LocalDate hoy = LocalDate.now();
    private Afiliado afiliado;
    private Mascota rocky;
    private Long cuotaId;

    @BeforeEach
    void afiliar() {
        // Plan con 1 atención incluida para poder vender bonos después de usarla.
        PlanResponse plan = planService.crearPlan(new CrearPlanRequest(
                "PAG" + System.nanoTime() % 100000, "Plan Pagos", new BigDecimal("50000"), 1, 1, hoy.minusDays(60)));
        afiliado = afiliadoRepository.saveAndFlush(new Afiliado(
                "LP-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana@example.com", "Calle 1"));
        rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(plan.id(), List.of(rocky.getId())));
        cuotaId = cuotaService.historial(afiliado.getId()).cuotas().get(0).id();
    }

    // Libro de pagos -------------------------------------------------------

    @Test
    void elPagoManualDeUnaCuotaQuedaAprobadoEnElLibroConSuOrigen() {
        cuotaService.registrarPago(afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.TRANSFERENCIA));

        assertThat(pagoService.transaccionesDelAfiliado(afiliado.getId())).singleElement().satisfies(pago -> {
            assertThat(pago.concepto()).isEqualTo("CUOTA_PLAN");
            assertThat(pago.cuotaId()).isEqualTo(cuotaId);
            assertThat(pago.canal()).isEqualTo("MANUAL");
            assertThat(pago.medioPago()).isEqualTo("TRANSFERENCIA");
            assertThat(pago.estado()).isEqualTo("APROBADO");
            assertThat(pago.monto()).isEqualByComparingTo("50000");
            assertThat(pago.descripcion()).startsWith("Cuota plan Plan Pagos del");
        });
    }

    @Test
    void laCompraManualDeUnBonoQuedaEnElLibro() {
        usarAtencionIncluida();

        bonoService.comprar(afiliado.getId(), new CompraBonoRequest(rocky.getId(), consultaGeneral(), MedioPago.TARJETA));

        assertThat(pagoService.transaccionesDelAfiliado(afiliado.getId()))
                .filteredOn(pago -> pago.concepto().equals("BONO"))
                .singleElement()
                .satisfies(pago -> {
                    assertThat(pago.medioPago()).isEqualTo("TARJETA");
                    assertThat(pago.estado()).isEqualTo("APROBADO");
                    assertThat(pago.descripcion()).contains("Consulta general", "Rocky");
                });
    }

    // Pasarela -------------------------------------------------------------

    @Test
    void elPagoEnLineaQuedaPendienteHastaQueLaPasarelaLoApruebe() {
        PagoEnLineaResponse sesion = pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId);
        assertThat(sesion.urlPago()).isEqualTo("/pasarela/checkout/" + sesion.referencia());
        assertThat(pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId).referencia())
                .as("no abre dos sesiones para la misma cuota").isEqualTo(sesion.referencia());
        assertThat(cuotaService.historial(afiliado.getId()).cuotas().get(0).estado()).isEqualTo("PENDIENTE");

        EventoPasarelaRequest aprobado = evento(sesion.referencia(), "APROBADO", "50000");
        PagoResponse pago = webhookService.procesar(aprobado, firma.firmar(aprobado));

        assertThat(pago.estado()).isEqualTo("APROBADO");
        assertThat(pago.canal()).isEqualTo("PASARELA");
        assertThat(pago.medioPago()).isEqualTo("PSE");
        assertThat(pago.transaccionPasarela()).isEqualTo("TX-1");
        assertThat(cuotaService.historial(afiliado.getId()).cuotas().get(0).estado()).isEqualTo("PAGADA");
        // Era la cuota de activación: el plan queda activo desde hoy.
        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).estado()).isEqualTo("ACTIVO");
        // Si la pasarela reenvía la notificación, no se aplica dos veces.
        assertThat(webhookService.procesar(aprobado, firma.firmar(aprobado)).estado()).isEqualTo("APROBADO");
    }

    @Test
    void siLaPasarelaRechazaElPagoLaCuotaSigueSinPagar() {
        PagoEnLineaResponse sesion = pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId);

        EventoPasarelaRequest rechazado = evento(sesion.referencia(), "RECHAZADO", "50000");
        assertThat(webhookService.procesar(rechazado, firma.firmar(rechazado)).estado()).isEqualTo("RECHAZADO");

        assertThat(cuotaService.historial(afiliado.getId()).cuotas().get(0).estado()).isEqualTo("PENDIENTE");
        assertThat(afiliacionService.obtenerPlan(afiliado.getId()).estado()).isEqualTo("PENDIENTE_ACTIVACION");
        // Se puede volver a intentar con una sesión nueva.
        assertThat(pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId).referencia()).isNotEqualTo(sesion.referencia());
    }

    @Test
    void elBonoPagadoEnLineaSoloSeUsaCuandoLaPasarelaConfirma() {
        usarAtencionIncluida();
        PagoEnLineaResponse sesion = bonoService.comprarEnLinea(
                afiliado.getId(), new CompraBonoEnLineaRequest(rocky.getId(), consultaGeneral()));
        Long bonoId = bonoService.listar(afiliado.getId()).get(0).id();
        assertThat(bonoService.listar(afiliado.getId()).get(0).estado()).isEqualTo("PENDIENTE_PAGO");

        BusinessRuleException sinPagar = assertThrows(BusinessRuleException.class, () -> prestacionService.registrar(
                afiliado.getId(), new RegistrarPrestacionRequest(rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, bonoId)));
        assertThat(sinPagar.getMessage()).contains("aún no se ha pagado");

        EventoPasarelaRequest aprobado = evento(sesion.referencia(), "APROBADO", sesion.monto().toPlainString());
        webhookService.procesar(aprobado, firma.firmar(aprobado));

        assertThat(bonoService.listar(afiliado.getId()).get(0).estado()).isEqualTo("DISPONIBLE");
    }

    @Test
    void siSeRechazaElPagoDelBonoElBonoSeAnula() {
        usarAtencionIncluida();
        PagoEnLineaResponse sesion = bonoService.comprarEnLinea(
                afiliado.getId(), new CompraBonoEnLineaRequest(rocky.getId(), consultaGeneral()));

        EventoPasarelaRequest rechazado = evento(sesion.referencia(), "RECHAZADO", sesion.monto().toPlainString());
        webhookService.procesar(rechazado, firma.firmar(rechazado));

        assertThat(bonoService.listar(afiliado.getId()).get(0).estado()).isEqualTo("ANULADO");
    }

    @Test
    void elWebhookRechazaNotificacionesSinFirmaValidaYNoCambiaNada() throws Exception {
        PagoEnLineaResponse sesion = pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId);
        EventoPasarelaRequest aprobado = evento(sesion.referencia(), "APROBADO", "50000");

        mockMvc.perform(post("/api/v1/pasarela/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Firma", "firma-falsa")
                        .content(json.writeValueAsString(aprobado)))
                .andExpect(status().isUnauthorized());
        assertThat(cuotaService.historial(afiliado.getId()).cuotas().get(0).estado()).isEqualTo("PENDIENTE");

        // Con la firma correcta lo acepta, sin sesión ni CSRF: lo llama la pasarela.
        mockMvc.perform(post("/api/v1/pasarela/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Firma", firma.firmar(aprobado))
                        .content(json.writeValueAsString(aprobado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void unaNotificacionConOtroMontoNoSeAplica() {
        PagoEnLineaResponse sesion = pagoService.iniciarPagoCuota(afiliado.getId(), cuotaId);
        EventoPasarelaRequest alterado = evento(sesion.referencia(), "APROBADO", "1000");

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> webhookService.procesar(alterado, firma.firmar(alterado)));

        assertThat(error.getMessage()).contains("no coincide");
        assertThat(cuotaService.historial(afiliado.getId()).cuotas().get(0).estado()).isEqualTo("PENDIENTE");
    }

    // Conciliación ---------------------------------------------------------

    @Test
    void laConciliacionTotalizaPorEstadoYMedioYFiltra() {
        cuotaService.registrarPago(afiliado.getId(), cuotaId, new RegistrarPagoRequest(hoy, MedioPago.EFECTIVO));
        usarAtencionIncluida();
        PagoEnLineaResponse pendiente = bonoService.comprarEnLinea(
                afiliado.getId(), new CompraBonoEnLineaRequest(rocky.getId(), consultaGeneral()));

        ConciliacionPagosResponse todo = pagoService.conciliacion(hoy, hoy, null, null, null, null);
        assertThat(todo.pagos()).extracting(PagoResponse::afiliadoId).contains(afiliado.getId());
        assertThat(todo.totalAprobado()).isGreaterThanOrEqualTo(new BigDecimal("50000"));
        assertThat(todo.totalPendiente()).isGreaterThanOrEqualTo(pendiente.monto());
        assertThat(todo.aprobadoPorMedio()).containsKey("EFECTIVO");

        ConciliacionPagosResponse soloPendientes = pagoService.conciliacion(hoy, hoy, EstadoPago.PENDIENTE, null, null, null);
        assertThat(soloPendientes.pagos()).allSatisfy(pago -> assertThat(pago.estado()).isEqualTo("PENDIENTE"));
        assertThat(soloPendientes.pagos()).extracting(PagoResponse::referencia).contains(pendiente.referencia());

        assertThrows(BusinessRuleException.class, () -> pagoService.conciliacion(hoy, hoy.minusDays(1), null, null, null, null));
    }

    /** Activa el plan (si falta) y usa su única atención incluida, para poder vender bonos. */
    private void usarAtencionIncluida() {
        ActivacionPlan.pagarActivacion(cuotaService, afiliado.getId());
        prestacionService.registrar(afiliado.getId(), new RegistrarPrestacionRequest(
                rocky.getId(), consultaGeneral(), hoy, "VetCasa", null, null));
    }

    private static EventoPasarelaRequest evento(String referencia, String estado, String monto) {
        return new EventoPasarelaRequest(referencia, estado, "TX-1", new BigDecimal(monto), MedioPago.PSE, null);
    }

    private Long consultaGeneral() {
        return servicioRepository.findAll().stream()
                .filter(servicio -> servicio.getCodigo().equals("CONSULTA_GENERAL"))
                .findFirst().orElseThrow().getId();
    }
}
