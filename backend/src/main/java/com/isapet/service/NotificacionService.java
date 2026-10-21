package com.isapet.service;

import com.isapet.dto.NotificacionResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoCuota;
import com.isapet.entity.EstadoNotificacion;
import com.isapet.entity.Mascota;
import com.isapet.entity.Notificacion;
import com.isapet.entity.TipoNotificacion;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.NotificacionRepository;
import com.isapet.repository.PrestacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** Arma y envía por correo los avisos al afiliado, y deja registro de cada envío. */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DIAS_AVISO_FIN_PERIODO = 2;

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final PrestacionRepository prestacionRepository;
    private final NotificacionRepository notificacionRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final boolean habilitadas;
    private final String remitente;

    public NotificacionService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            PrestacionRepository prestacionRepository,
            NotificacionRepository notificacionRepository,
            CuotaPlanRepository cuotaRepository,
            BeneficiosPlanResolver beneficiosResolver,
            JavaMailSender mailSender,
            Clock clock,
            @Value("${app.notificaciones.habilitadas:true}") boolean habilitadas,
            @Value("${app.notificaciones.remitente:ISAPET <no-reply@isapet.local>}") String remitente) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.prestacionRepository = prestacionRepository;
        this.notificacionRepository = notificacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.beneficiosResolver = beneficiosResolver;
        this.mailSender = mailSender;
        this.clock = clock;
        this.habilitadas = habilitadas;
        this.remitente = remitente;
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return notificacionRepository.findAllByAfiliadoIdOrderByCreadaEnDescIdDesc(afiliadoId).stream()
                .map(notificacion -> new NotificacionResponse(
                        notificacion.getId(),
                        notificacion.getTipo().name(),
                        notificacion.getDestinatario(),
                        notificacion.getAsunto(),
                        notificacion.getEstado().name(),
                        notificacion.getError(),
                        notificacion.getCreadaEn()))
                .toList();
    }

    @Transactional
    public void bienvenida(Long afiliadoId) {
        afiliadoRepository.findById(afiliadoId).ifPresent(afiliado -> enviar(
                afiliado,
                TipoNotificacion.BIENVENIDA,
                "BIENVENIDA:" + afiliadoId,
                "Bienvenido a ISAPET",
                """
                Hola %s:

                Te damos la bienvenida a ISAPET. Ya quedaste registrado como afiliado con la identificación %s.
                Pronto podrás activar un plan para cubrir las atenciones veterinarias de tus mascotas.
                """.formatted(afiliado.getNombres(), afiliado.getNumeroIdentificacion())));
    }

    @Transactional
    public void planActivado(Long afiliadoId) {
        afiliacionRepository.findByAfiliadoId(afiliadoId).ifPresent(afiliacion -> {
            Afiliado afiliado = afiliacion.getAfiliado();
            PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), afiliacion.getFechaInicio());
            Optional<VersionPlan> version = beneficiosResolver.versionVigente(
                    afiliacion.getPlan().getId(), afiliacion.getFechaInicio());
            String mascotas = afiliacion.getMascotasCubiertas().stream()
                    .map(Mascota::getNombre)
                    .sorted()
                    .collect(Collectors.joining(", "));
            enviar(
                    afiliado,
                    TipoNotificacion.PLAN_ACTIVADO,
                    "PLAN_ACTIVADO:%d:%d:%s".formatted(afiliadoId, afiliacion.getPlan().getId(), afiliacion.getFechaInicio()),
                    "Tu plan " + afiliacion.getPlan().getNombre() + " está activo",
                    """
                    Hola %s:

                    Tu plan %s quedó activo desde el %s.
                    Mascotas cubiertas: %s.
                    Valor mensual: %s.
                    Atenciones incluidas: %s.

                    Tu primer periodo va del %s al %s. Al terminar cada periodo se renuevan las atenciones incluidas.
                    """.formatted(
                            afiliado.getNombres(),
                            afiliacion.getPlan().getNombre(),
                            afiliacion.getFechaInicio().format(FECHA),
                            mascotas,
                            version.map(v -> pesos(v.getPrecioMensual())).orElse("—"),
                            version.map(v -> atencionesIncluidas(v.getLimiteAtenciones())).orElse("—"),
                            periodo.desde().format(FECHA),
                            periodo.hasta().format(FECHA)));
        });
    }

    @Transactional
    public void atencionesAgotadas(Long afiliadoId, LocalDate periodoDesde) {
        afiliacionRepository.findByAfiliadoId(afiliadoId).ifPresent(afiliacion -> {
            Afiliado afiliado = afiliacion.getAfiliado();
            PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), periodoDesde);
            enviar(
                    afiliado,
                    TipoNotificacion.ATENCIONES_AGOTADAS,
                    "ATENCIONES_AGOTADAS:%d:%s".formatted(afiliadoId, periodo.desde()),
                    "Usaste todas las atenciones incluidas de tu plan",
                    """
                    Hola %s:

                    Ya usaste todas las atenciones incluidas en tu plan %s para el periodo del %s al %s.
                    Si necesitas más atenciones antes del %s, puedes comprar bonos con el descuento de tu plan.
                    """.formatted(
                            afiliado.getNombres(),
                            afiliacion.getPlan().getNombre(),
                            periodo.desde().format(FECHA),
                            periodo.hasta().format(FECHA),
                            periodo.hastaExclusiva().format(FECHA)));
        });
    }

    @Transactional
    public void planCancelado(Long afiliadoId, String plan, LocalDate ultimoDia, boolean inmediata) {
        afiliadoRepository.findById(afiliadoId).ifPresent(afiliado -> enviar(
                afiliado,
                TipoNotificacion.PLAN_CANCELADO,
                "PLAN_CANCELADO:%d:%s".formatted(afiliadoId, OffsetDateTime.now(clock).toInstant()),
                "Cancelaste tu plan " + plan,
                inmediata
                        ? """
                        Hola %s:

                        Tu plan %s quedó cancelado hoy, %s. Ya no tienes atenciones incluidas ni puedes comprar bonos.
                        Si tenías bonos sin usar, los puedes seguir usando.
                        """.formatted(afiliado.getNombres(), plan, ultimoDia.format(FECHA))
                        : """
                        Hola %s:

                        Registramos la cancelación de tu plan %s. Lo conservas, con sus atenciones incluidas,
                        hasta el %s. Desde el día siguiente ya no tendrás plan y no se generarán más cuotas.
                        Si cambias de opinión antes de esa fecha, puedes deshacer la cancelación.
                        """.formatted(afiliado.getNombres(), plan, ultimoDia.format(FECHA))));
    }

    @Transactional
    public void planReactivado(Long afiliadoId) {
        afiliacionRepository.findByAfiliadoId(afiliadoId).ifPresent(afiliacion -> enviar(
                afiliacion.getAfiliado(),
                TipoNotificacion.PLAN_REACTIVADO,
                "PLAN_REACTIVADO:%d:%s".formatted(afiliadoId, OffsetDateTime.now(clock).toInstant()),
                "Tu plan " + afiliacion.getPlan().getNombre() + " está activo de nuevo",
                """
                Hola %s:

                Recibimos tu pago y tu plan %s está activo de nuevo. Ya puedes usar tus atenciones incluidas y comprar bonos.
                """.formatted(afiliacion.getAfiliado().getNombres(), afiliacion.getPlan().getNombre())));
    }

    /**
     * Avisa la suspensión a quien tenga una cuota pendiente con el plazo de pago vencido.
     * Cada cuota vencida se avisa una sola vez.
     */
    @Transactional
    public int notificarSuspensiones(LocalDate hoy) {
        int generados = 0;
        for (CuotaPlan cuota : cuotaRepository.findAllByEstadoAndDeActivacionFalseAndPeriodoDesdeLessThan(
                EstadoCuota.PENDIENTE, hoy.minusDays(CuotaPlan.DIAS_PLAZO_PAGO))) {
            Afiliado afiliado = cuota.getAfiliado();
            String clave = "PLAN_SUSPENDIDO:%d:%d".formatted(afiliado.getId(), cuota.getId());
            if (notificacionRepository.existsByClave(clave) || afiliacionRepository.findByAfiliadoId(afiliado.getId()).isEmpty()) {
                continue;
            }
            enviar(
                    afiliado,
                    TipoNotificacion.PLAN_SUSPENDIDO,
                    clave,
                    "Tu plan " + cuota.getPlan().getNombre() + " está suspendido por falta de pago",
                    """
                    Hola %s:

                    No recibimos el pago de la cuota de tu plan %s por el periodo del %s al %s (%s).
                    El plazo para pagar venció el %s, así que el plan quedó suspendido: mientras tanto no puedes
                    usar tus atenciones incluidas ni comprar bonos. Cuando pagues, se reactivará automáticamente.
                    """.formatted(
                            afiliado.getNombres(),
                            cuota.getPlan().getNombre(),
                            cuota.getPeriodoDesde().format(FECHA),
                            cuota.getPeriodoHasta().format(FECHA),
                            pesos(cuota.getValor()),
                            cuota.fechaLimitePago().format(FECHA)));
            generados++;
        }
        return generados;
    }

    /**
     * Avisa a cada afiliado que su periodo termina en {@value #DIAS_AVISO_FIN_PERIODO} días o menos.
     * Es idempotente: cada periodo se avisa una sola vez aunque se ejecute varias veces.
     *
     * @return cantidad de avisos nuevos generados.
     */
    @Transactional
    public int recordarFinDePeriodo(LocalDate hoy) {
        int generados = 0;
        for (Afiliacion afiliacion : afiliacionRepository.findAllBy()) {
            // Quien canceló ya recibió el aviso de hasta cuándo conserva el plan.
            if (hoy.isBefore(afiliacion.getFechaInicio()) || afiliacion.tieneCancelacionProgramada()
                    || afiliacion.isPendienteActivacion()) {
                continue;
            }
            PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), hoy);
            if (hoy.isBefore(periodo.hasta().minusDays(DIAS_AVISO_FIN_PERIODO))) {
                continue;
            }
            Afiliado afiliado = afiliacion.getAfiliado();
            String clave = "FIN_PERIODO:%d:%s".formatted(afiliado.getId(), periodo.desde());
            if (notificacionRepository.existsByClave(clave)) {
                continue;
            }
            Integer limite = beneficiosResolver.versionVigente(afiliacion.getPlan().getId(), hoy)
                    .map(VersionPlan::getLimiteAtenciones)
                    .orElse(null);
            String saldo = limite == null
                    ? "Tu plan incluye atenciones ilimitadas."
                    : "Te quedan %d de %d atenciones incluidas sin usar; no se acumulan para el siguiente periodo.".formatted(
                            Math.max(0, limite - prestacionRepository.contarIncluidasEnPlan(
                                    afiliado.getId(), periodo.desde(), periodo.hastaExclusiva())),
                            limite);
            enviar(
                    afiliado,
                    TipoNotificacion.FIN_PERIODO,
                    clave,
                    "Tu periodo del plan " + afiliacion.getPlan().getNombre() + " termina el " + periodo.hasta().format(FECHA),
                    """
                    Hola %s:

                    Tu periodo actual del plan %s termina el %s. %s
                    El %s empieza un nuevo periodo con tus atenciones incluidas renovadas.
                    """.formatted(
                            afiliado.getNombres(),
                            afiliacion.getPlan().getNombre(),
                            periodo.hasta().format(FECHA),
                            saldo,
                            periodo.hastaExclusiva().format(FECHA)));
            generados++;
        }
        return generados;
    }

    private void enviar(Afiliado afiliado, TipoNotificacion tipo, String clave, String asunto, String cuerpo) {
        if (!habilitadas || notificacionRepository.existsByClave(clave)) {
            return;
        }
        String destinatario = afiliado.getCorreo();
        EstadoNotificacion estado = EstadoNotificacion.ENVIADA;
        String error = null;
        if (destinatario == null || destinatario.isBlank()) {
            estado = EstadoNotificacion.FALLIDA;
            error = "El afiliado no tiene correo electrónico registrado.";
        } else {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setFrom(remitente);
                mensaje.setTo(destinatario);
                mensaje.setSubject(asunto);
                mensaje.setText(cuerpo.strip() + "\n\nEquipo ISAPET\n");
                mailSender.send(mensaje);
            } catch (MailException exception) {
                log.warn("No se pudo enviar la notificación {} a {}", clave, destinatario, exception);
                estado = EstadoNotificacion.FALLIDA;
                error = recortar(exception.getMessage());
            }
        }
        notificacionRepository.save(new Notificacion(
                afiliado, tipo, clave, destinatario, asunto, estado, error, OffsetDateTime.now(clock)));
    }

    private static String atencionesIncluidas(Integer limite) {
        return limite == null ? "ilimitadas" : limite + " por periodo";
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        formato.setMaximumFractionDigits(0);
        return formato.format(valor);
    }

    private static String recortar(String mensaje) {
        if (mensaje == null) {
            return "Error desconocido al enviar el correo.";
        }
        return mensaje.length() <= 500 ? mensaje : mensaje.substring(0, 500);
    }
}
