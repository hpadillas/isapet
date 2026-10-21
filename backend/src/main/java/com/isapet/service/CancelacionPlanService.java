package com.isapet.service;

import com.isapet.dto.CancelacionPlanResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoCuota;
import com.isapet.exception.AfiliacionNotFoundException;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cancelación del plan por parte del afiliado.
 * <ul>
 *   <li>Con la cuota del periodo actual pagada: conserva el plan hasta el final del periodo; se puede deshacer.</li>
 *   <li>Dentro del plazo de pago y sin haber pagado: termina hoy y la cuota del periodo se anula.</li>
 *   <li>Con el plan suspendido por falta de pago: termina hoy; las cuotas vencidas siguen como deuda.</li>
 * </ul>
 */
@Service
public class CancelacionPlanService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final CuotaPlanService cuotaService;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public CancelacionPlanService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            CuotaPlanRepository cuotaRepository,
            CuotaPlanService cuotaService,
            ApplicationEventPublisher eventos,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.cuotaService = cuotaService;
        this.eventos = eventos;
        this.clock = clock;
    }

    /** Lo que pasaría si el afiliado cancela hoy, sin cancelar. */
    @Transactional
    public CancelacionPlanResponse simular(Long afiliadoId) {
        Afiliacion afiliacion = afiliacionCancelable(afiliadoId);
        return decidir(afiliacion, LocalDate.now(clock)).response(afiliacion);
    }

    @Transactional
    public CancelacionPlanResponse cancelar(Long afiliadoId) {
        Afiliacion afiliacion = afiliacionCancelable(afiliadoId);
        LocalDate hoy = LocalDate.now(clock);
        Decision decision = decidir(afiliacion, hoy);
        CancelacionPlanResponse response = decision.response(afiliacion);

        if (decision.inmediata()) {
            if (decision.cuotaAAnular() != null) {
                decision.cuotaAAnular().anular(OffsetDateTime.now(clock));
            }
            afiliacionRepository.delete(afiliacion);
        } else {
            afiliacion.programarCancelacion(decision.ultimoDia());
        }
        afiliacionRepository.flush();
        eventos.publishEvent(new EventoAfiliado.PlanCancelado(
                afiliadoId, response.plan(), decision.ultimoDia(), decision.inmediata()));
        return response;
    }

    @Transactional
    public void deshacer(Long afiliadoId) {
        Afiliacion afiliacion = afiliacion(afiliadoId);
        if (!afiliacion.tieneCancelacionProgramada()) {
            throw new BusinessRuleException("El plan del afiliado no tiene una cancelación programada.");
        }
        afiliacion.deshacerCancelacion();
        afiliacionRepository.flush();
    }

    /**
     * Termina las afiliaciones cuya cancelación ya se cumplió (el último día quedó atrás).
     *
     * @return cantidad de afiliaciones terminadas.
     */
    @Transactional
    public int aplicarCancelacionesCumplidas(LocalDate hoy) {
        List<Afiliacion> cumplidas = afiliacionRepository.findConCancelacionCumplidaForUpdate(hoy);
        afiliacionRepository.deleteAll(cumplidas);
        afiliacionRepository.flush();
        return cumplidas.size();
    }

    private Decision decidir(Afiliacion afiliacion, LocalDate hoy) {
        Long afiliadoId = afiliacion.getAfiliado().getId();
        // Si la tarea diaria aún no generó la cuota del periodo actual, se genera antes de decidir.
        cuotaService.generarCuotas(afiliacion, hoy);
        List<CuotaPlan> pendientes = cuotaRepository.findAllByAfiliadoIdAndEstadoOrderByPeriodoDesdeAsc(
                afiliadoId, EstadoCuota.PENDIENTE);

        if (afiliacion.isPendienteActivacion()) {
            CuotaPlan activacion = pendientes.stream().filter(CuotaPlan::isDeActivacion).findFirst().orElse(null);
            return new Decision(true, hoy, activacion, BigDecimal.ZERO,
                    "El plan aún no se ha activado porque no se pagó su primera cuota: se cancela hoy y la cuota se anula.");
        }

        BigDecimal deuda = pendientes.stream()
                .filter(cuota -> cuota.estaVencida(hoy))
                .map(CuotaPlan::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (deuda.signum() > 0) {
            return new Decision(true, hoy, null, deuda,
                    "El plan está suspendido por falta de pago: termina hoy y las cuotas vencidas siguen pendientes.");
        }

        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), hoy);
        CuotaPlan cuotaActual = pendientes.stream()
                .filter(cuota -> cuota.getPeriodoDesde().equals(periodo.desde()))
                .findFirst()
                .orElse(null);
        if (cuotaActual != null) {
            return new Decision(true, hoy, cuotaActual, BigDecimal.ZERO,
                    "La cuota del periodo actual no se ha pagado y sigue dentro del plazo de pago (hasta el "
                            + cuotaActual.fechaLimitePago() + "): el plan termina hoy y la cuota se anula.");
        }
        return new Decision(false, periodo.hasta(), null, BigDecimal.ZERO,
                "El periodo actual está pagado: el plan sigue activo hasta el último día del periodo.");
    }

    private Afiliacion afiliacionCancelable(Long afiliadoId) {
        Afiliacion afiliacion = afiliacion(afiliadoId);
        if (afiliacion.tieneCancelacionProgramada()) {
            throw new BusinessRuleException(
                    "El plan ya tiene una cancelación programada; termina el " + afiliacion.getFechaFin() + ".");
        }
        return afiliacion;
    }

    private Afiliacion afiliacion(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId)
                .orElseThrow(() -> new AfiliacionNotFoundException(afiliadoId));
    }

    private record Decision(
            boolean inmediata,
            LocalDate ultimoDia,
            CuotaPlan cuotaAAnular,
            BigDecimal deudaPendiente,
            String motivo) {

        CancelacionPlanResponse response(Afiliacion afiliacion) {
            return new CancelacionPlanResponse(
                    afiliacion.getPlan().getNombre(),
                    inmediata,
                    ultimoDia,
                    motivo,
                    cuotaAAnular == null ? BigDecimal.ZERO : cuotaAAnular.getValor(),
                    deudaPendiente);
        }
    }
}
