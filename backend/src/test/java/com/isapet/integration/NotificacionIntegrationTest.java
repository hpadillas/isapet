package com.isapet.integration;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.NotificacionResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.service.AfiliacionService;
import com.isapet.service.CuotaPlanService;
import com.isapet.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Contenido, destinatario e idempotencia de las notificaciones, con el envío de correo simulado.
 * Cada prueba se revierte al terminar.
 */
@SpringBootTest
@Transactional
class NotificacionIntegrationTest {

    @MockitoBean private JavaMailSender mailSender;
    @Autowired private NotificacionService notificacionService;
    @Autowired private AfiliacionService afiliacionService;
    @Autowired private CuotaPlanService cuotaService;
    @Autowired private AfiliadoRepository afiliadoRepository;
    @Autowired private MascotaRepository mascotaRepository;
    @Autowired private PlanRepository planRepository;

    private Afiliado afiliado;
    private Mascota rocky;

    @BeforeEach
    void prepararAfiliado() {
        afiliado = afiliadoRepository.saveAndFlush(new Afiliado(
                "NT-" + System.nanoTime(), "Ana", "Gómez", "3001234567", "ana.notif@example.com", "Calle 1"));
        rocky = mascotaRepository.saveAndFlush(new Mascota("Rocky", "Perro", "Labrador", 3, "Café", afiliado));
    }

    @Test
    void laBienvenidaSeEnviaAlCorreoDelAfiliadoUnaSolaVez() {
        notificacionService.bienvenida(afiliado.getId());
        notificacionService.bienvenida(afiliado.getId());

        SimpleMailMessage mensaje = mensajeEnviado();
        assertThat(mensaje.getTo()).containsExactly("ana.notif@example.com");
        assertThat(mensaje.getText()).contains("Hola Ana", afiliado.getNumeroIdentificacion());
        List<NotificacionResponse> registradas = notificacionService.listar(afiliado.getId());
        assertThat(registradas).singleElement().satisfies(notificacion -> {
            assertThat(notificacion.tipo()).isEqualTo("BIENVENIDA");
            assertThat(notificacion.estado()).isEqualTo("ENVIADA");
        });
    }

    @Test
    void elAvisoDePlanActivadoIncluyeElPeriodo() {
        asignarPlanBasico();

        notificacionService.planActivado(afiliado.getId());

        LocalDate hoy = LocalDate.now();
        assertThat(mensajeEnviado().getText())
                .contains("plan Básico", "Rocky", "2 por periodo")
                .contains(hoy.plusMonths(1).minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    void elRecordatorioSeEnviaDosDiasAntesDelFinDelPeriodoYNoSeRepite() {
        asignarPlanBasico();
        LocalDate finPeriodo = LocalDate.now().plusMonths(1).minusDays(1);

        notificacionService.recordarFinDePeriodo(finPeriodo.minusDays(3));
        assertThat(notificacionService.listar(afiliado.getId())).isEmpty();

        notificacionService.recordarFinDePeriodo(finPeriodo.minusDays(2));
        notificacionService.recordarFinDePeriodo(finPeriodo.minusDays(1));

        assertThat(notificacionService.listar(afiliado.getId())).singleElement()
                .satisfies(notificacion -> assertThat(notificacion.tipo()).isEqualTo("FIN_PERIODO"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(mensaje -> List.of(mensaje.getTo()).contains("ana.notif@example.com"))
                .singleElement()
                .satisfies(mensaje -> assertThat(mensaje.getText()).contains("Te quedan 2 de 2 atenciones incluidas"));
    }

    @Test
    void unFalloDelCorreoQuedaRegistradoSinInterrumpir() {
        doThrow(new MailSendException("SMTP no disponible")).when(mailSender).send(any(SimpleMailMessage.class));

        notificacionService.bienvenida(afiliado.getId());

        assertThat(notificacionService.listar(afiliado.getId())).singleElement().satisfies(notificacion -> {
            assertThat(notificacion.estado()).isEqualTo("FALLIDA");
            assertThat(notificacion.error()).contains("SMTP no disponible");
        });
    }

    private void asignarPlanBasico() {
        Long basico = planRepository.findAllByOrderByCodigoAsc().stream()
                .filter(plan -> plan.getCodigo().equalsIgnoreCase("BASICO"))
                .findFirst().orElseThrow().getId();
        afiliacionService.asignarPlan(afiliado.getId(), new AsignarPlanRequest(basico, List.of(rocky.getId())));
        ActivacionPlan.pagarActivacion(cuotaService, afiliado.getId());
    }

    private SimpleMailMessage mensajeEnviado() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
