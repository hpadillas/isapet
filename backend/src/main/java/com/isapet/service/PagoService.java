package com.isapet.service;

import com.isapet.dto.ConciliacionPagosResponse;
import com.isapet.dto.EventoPasarelaRequest;
import com.isapet.dto.PagoEnLineaResponse;
import com.isapet.dto.PagoResponse;
import com.isapet.dto.SesionPagoResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Bono;
import com.isapet.entity.CanalPago;
import com.isapet.entity.ConceptoPago;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoPago;
import com.isapet.entity.MedioPago;
import com.isapet.entity.Pago;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.CuotaNotFoundException;
import com.isapet.exception.PagoNotFoundException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.PagoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Libro de pagos: cada mensualidad o bono pagado queda como una transacción con su origen, medio, canal y
 * estado. Los pagos manuales se aprueban al registrarse; los pagos en línea quedan pendientes hasta que la
 * pasarela confirma el resultado por el webhook firmado.
 */
@Service
public class PagoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAXIMO_DIAS_CONCILIACION = 366;

    private final PagoRepository pagoRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final PasarelaPagos pasarela;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public PagoService(
            PagoRepository pagoRepository,
            CuotaPlanRepository cuotaRepository,
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            PasarelaPagos pasarela,
            ApplicationEventPublisher eventos,
            Clock clock) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.pasarela = pasarela;
        this.eventos = eventos;
        this.clock = clock;
    }

    // --- Pagos registrados por el administrador ------------------------------------------------

    /** Pago de cuota en ISAPET (efectivo, datáfono, transferencia): queda aprobado y salda la cuota. */
    @Transactional
    public Pago registrarManualCuota(CuotaPlan cuota, LocalDate fechaPago, MedioPago medioPago) {
        Pago pago = pagoRepository.save(Pago.deCuota(cuota, CanalPago.MANUAL, ahora()));
        pago.aprobar(fechaPago, medioPago, null, ahora());
        saldarCuota(cuota, fechaPago, medioPago);
        return pago;
    }

    @Transactional
    public Pago registrarManualBono(Bono bono, MedioPago medioPago) {
        Pago pago = pagoRepository.save(Pago.deBono(bono, CanalPago.MANUAL, ahora()));
        pago.aprobar(LocalDate.now(clock), medioPago, null, ahora());
        return pago;
    }

    // --- Pagos en línea -------------------------------------------------------------------------

    /** Abre (o reutiliza, si ya hay una sin terminar) la sesión de pago en línea de una cuota pendiente. */
    @Transactional
    public PagoEnLineaResponse iniciarPagoCuota(Long afiliadoId, Long cuotaId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        CuotaPlan cuota = cuotaRepository.findByIdAndAfiliadoIdForUpdate(cuotaId, afiliadoId)
                .orElseThrow(() -> new CuotaNotFoundException(cuotaId));
        if (!cuota.estaPendiente()) {
            throw new BusinessRuleException("La cuota no está pendiente de pago.");
        }
        Pago pago = pagoRepository.findFirstByCuotaIdAndEstado(cuotaId, EstadoPago.PENDIENTE)
                .orElseGet(() -> pagoRepository.save(Pago.deCuota(cuota, CanalPago.PASARELA, ahora())));
        return enLinea(pago);
    }

    @Transactional
    public PagoEnLineaResponse iniciarPagoBono(Bono bono) {
        if (bono.getValorPagado().signum() == 0) {
            throw new BusinessRuleException("El bono no tiene valor a pagar; regístralo como compra en ISAPET.");
        }
        return enLinea(pagoRepository.save(Pago.deBono(bono, CanalPago.PASARELA, ahora())));
    }

    /**
     * Aplica el resultado confirmado por la pasarela. Es idempotente: si la pasarela reenvía la
     * notificación de un pago ya procesado, no se vuelve a aplicar.
     */
    @Transactional
    public PagoResponse aplicarResultado(EventoPasarelaRequest evento) {
        Pago pago = pagoRepository.findByReferenciaForUpdate(evento.referencia())
                .orElseThrow(() -> new PagoNotFoundException(evento.referencia()));
        if (!pago.estaPendiente()) {
            return toResponse(pago);
        }
        if (pago.getMonto().compareTo(evento.monto()) != 0) {
            throw new BusinessRuleException("El monto notificado por la pasarela (%s) no coincide con el del pago (%s)."
                    .formatted(evento.monto().toPlainString(), pago.getMonto().toPlainString()));
        }

        if (evento.estado().equals("APROBADO")) {
            pago.aprobar(LocalDate.now(clock), evento.medioPago(), evento.transaccion(), ahora());
            if (pago.getConcepto() == ConceptoPago.CUOTA_PLAN) {
                CuotaPlan cuota = pago.getCuota();
                if (cuota.estaPendiente()) {
                    saldarCuota(cuota, pago.getFechaPago(), evento.medioPago());
                } else {
                    pago.anotar("La cuota ya no estaba pendiente al aprobarse el pago: revisar para devolución.");
                }
            } else {
                pago.getBono().confirmarPago();
            }
        } else {
            pago.rechazar(evento.medioPago(), evento.transaccion(),
                    evento.motivo() == null || evento.motivo().isBlank() ? "Rechazado por la pasarela." : evento.motivo(),
                    ahora());
            if (pago.getConcepto() == ConceptoPago.BONO) {
                pago.getBono().anularPorPagoRechazado();
            }
        }
        pagoRepository.flush();
        return toResponse(pago);
    }

    // --- Consultas --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SesionPagoResponse sesion(String referencia) {
        Pago pago = pagoRepository.findByReferencia(referencia).orElseThrow(() -> new PagoNotFoundException(referencia));
        return new SesionPagoResponse(
                pago.getReferencia(),
                pago.getAfiliado().getId(),
                nombre(pago),
                pago.getConcepto().name(),
                descripcion(pago),
                pago.getMonto(),
                pago.getEstado().name());
    }

    @Transactional(readOnly = true)
    public List<PagoResponse> transaccionesDelAfiliado(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return pagoRepository.findAllByAfiliadoIdOrderByCreadoEnDescIdDesc(afiliadoId).stream()
                .map(PagoService::toResponse)
                .toList();
    }

    /** Pagos registrados entre dos fechas (inclusive), con filtros opcionales y totales para conciliar. */
    @Transactional(readOnly = true)
    public ConciliacionPagosResponse conciliacion(
            LocalDate desde, LocalDate hasta, EstadoPago estado, ConceptoPago concepto, MedioPago medio, CanalPago canal) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate inicio = desde == null ? hoy.minusDays(30) : desde;
        LocalDate fin = hasta == null ? hoy : hasta;
        if (fin.isBefore(inicio)) {
            throw new BusinessRuleException("La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > MAXIMO_DIAS_CONCILIACION) {
            throw new BusinessRuleException("El rango de conciliación no puede superar un año.");
        }

        List<Pago> pagos = pagoRepository.findAllByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByCreadoEnDescIdDesc(
                        inicio.atStartOfDay(clock.getZone()).toOffsetDateTime(),
                        fin.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime()).stream()
                .filter(pago -> estado == null || pago.getEstado() == estado)
                .filter(pago -> concepto == null || pago.getConcepto() == concepto)
                .filter(pago -> medio == null || pago.getMedioPago() == medio)
                .filter(pago -> canal == null || pago.getCanal() == canal)
                .toList();

        Map<String, BigDecimal> porMedio = new TreeMap<>();
        BigDecimal aprobado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        BigDecimal rechazado = BigDecimal.ZERO;
        for (Pago pago : pagos) {
            switch (pago.getEstado()) {
                case APROBADO -> {
                    aprobado = aprobado.add(pago.getMonto());
                    String clave = pago.getMedioPago() == null ? "NO_REGISTRADO" : pago.getMedioPago().name();
                    porMedio.merge(clave, pago.getMonto(), BigDecimal::add);
                }
                case PENDIENTE -> pendiente = pendiente.add(pago.getMonto());
                case RECHAZADO -> rechazado = rechazado.add(pago.getMonto());
                default -> {
                }
            }
        }
        return new ConciliacionPagosResponse(inicio, fin, pagos.stream().map(PagoService::toResponse).toList(),
                aprobado, pendiente, rechazado, porMedio);
    }

    // --- Apoyo ------------------------------------------------------------------------------------

    private void saldarCuota(CuotaPlan cuota, LocalDate fechaPago, MedioPago medioPago) {
        Long afiliadoId = cuota.getAfiliado().getId();
        LocalDate hoy = LocalDate.now(clock);
        if (cuota.isDeActivacion()) {
            activarPlan(cuota, fechaPago);
        }
        boolean estabaSuspendido = cuotaRepository.planSuspendido(afiliadoId, hoy);
        cuota.registrarPago(fechaPago, medioPago, ahora());
        cuotaRepository.flush();
        // Al pagar la última cuota vencida, el plan suspendido se reactiva.
        if (estabaSuspendido && !cuotaRepository.planSuspendido(afiliadoId, hoy)) {
            eventos.publishEvent(new EventoAfiliado.PlanReactivado(afiliadoId));
        }
    }

    /**
     * Pagar la cuota de activación activa el plan: el primer periodo empieza el día del pago (no antes de
     * la asignación) y la cuota pasa a cubrir ese periodo.
     */
    private void activarPlan(CuotaPlan cuota, LocalDate fechaPago) {
        afiliacionRepository.findByAfiliadoIdForUpdate(cuota.getAfiliado().getId())
                .filter(Afiliacion::isPendienteActivacion)
                .ifPresent(afiliacion -> {
                    LocalDate inicio = fechaPago.isBefore(afiliacion.getFechaInicio()) ? afiliacion.getFechaInicio() : fechaPago;
                    afiliacion.activar(inicio);
                    PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(inicio, inicio);
                    cuota.reprogramarPeriodo(periodo.desde(), periodo.hasta());
                    eventos.publishEvent(new EventoAfiliado.PlanActivado(afiliacion.getAfiliado().getId()));
                });
    }

    private PagoEnLineaResponse enLinea(Pago pago) {
        return new PagoEnLineaResponse(pago.getReferencia(), pasarela.urlDePago(pago), pago.getMonto(), descripcion(pago));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(clock);
    }

    static PagoResponse toResponse(Pago pago) {
        return new PagoResponse(
                pago.getId(),
                pago.getReferencia(),
                pago.getAfiliado().getId(),
                nombre(pago),
                pago.getConcepto().name(),
                descripcion(pago),
                pago.getCuota() == null ? null : pago.getCuota().getId(),
                pago.getBono() == null ? null : pago.getBono().getId(),
                pago.getMonto(),
                pago.getMedioPago() == null ? null : pago.getMedioPago().name(),
                pago.getCanal().name(),
                pago.getEstado().name(),
                pago.getFechaPago(),
                pago.getCreadoEn(),
                pago.getConfirmadoEn(),
                pago.getTransaccionPasarela(),
                pago.getDetalle());
    }

    private static String nombre(Pago pago) {
        return pago.getAfiliado().getNombres() + " " + pago.getAfiliado().getApellidos();
    }

    private static String descripcion(Pago pago) {
        if (pago.getConcepto() == ConceptoPago.CUOTA_PLAN) {
            CuotaPlan cuota = pago.getCuota();
            return "Cuota plan %s del %s al %s".formatted(
                    cuota.getPlan().getNombre(), cuota.getPeriodoDesde().format(FECHA), cuota.getPeriodoHasta().format(FECHA));
        }
        Bono bono = pago.getBono();
        return "Bono %s · %s · %s".formatted(bono.getCodigo(), bono.getServicio().getNombre(), bono.getMascota().getNombre());
    }
}
