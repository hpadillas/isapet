package com.isapet.service;

import com.isapet.dto.CuotaPlanResponse;
import com.isapet.dto.HistorialPagosResponse;
import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoCuota;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.CuotaNotFoundException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Mensualidades del plan: una cuota pendiente por periodo de afiliación y el registro de su pago. */
@Service
public class CuotaPlanService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final PagoService pagoService;
    private final Clock clock;

    public CuotaPlanService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            CuotaPlanRepository cuotaRepository,
            BeneficiosPlanResolver beneficiosResolver,
            PagoService pagoService,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.beneficiosResolver = beneficiosResolver;
        this.pagoService = pagoService;
        this.clock = clock;
    }

    /**
     * Crea las cuotas que falten desde el inicio de la afiliación hasta el periodo que contiene {@code hoy}.
     * Es idempotente: un periodo nunca genera dos cuotas.
     */
    @Transactional
    public void generarCuotas(Afiliacion afiliacion, LocalDate hoy) {
        LocalDate inicio = afiliacion.getFechaInicio();
        if (hoy.isBefore(inicio)) {
            return;
        }
        // Sin activar, solo existe la cuota de activación; los periodos empiezan cuando se paga.
        if (afiliacion.isPendienteActivacion()) {
            generarCuotaDeActivacion(afiliacion);
            return;
        }
        LocalDate ultimoPeriodo = PeriodoAfiliacion.queContiene(inicio, hoy).desde();
        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(inicio, inicio);
        while (!periodo.desde().isAfter(ultimoPeriodo)) {
            // Con la cancelación programada no se generan cuotas de periodos posteriores al último día.
            if (afiliacion.tieneCancelacionProgramada() && periodo.desde().isAfter(afiliacion.getFechaFin())) {
                return;
            }
            Long afiliadoId = afiliacion.getAfiliado().getId();
            if (!cuotaRepository.existsByAfiliadoIdAndPeriodoDesdeAndEstadoNot(afiliadoId, periodo.desde(), EstadoCuota.ANULADA)) {
                Optional<BigDecimal> precio = beneficiosResolver.versionVigente(afiliacion.getPlan().getId(), periodo.desde())
                        .map(VersionPlan::getPrecioMensual);
                if (precio.isPresent()) {
                    cuotaRepository.save(new CuotaPlan(
                            afiliacion.getAfiliado(), afiliacion.getPlan(), periodo.desde(), periodo.hasta(), precio.get()));
                }
            }
            periodo = PeriodoAfiliacion.queContiene(inicio, periodo.hastaExclusiva());
        }
    }

    private void generarCuotaDeActivacion(Afiliacion afiliacion) {
        Long afiliadoId = afiliacion.getAfiliado().getId();
        boolean existe = cuotaRepository.findAllByAfiliadoIdAndEstadoOrderByPeriodoDesdeAsc(afiliadoId, EstadoCuota.PENDIENTE)
                .stream().anyMatch(CuotaPlan::isDeActivacion);
        if (existe) {
            return;
        }
        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), afiliacion.getFechaInicio());
        beneficiosResolver.versionVigente(afiliacion.getPlan().getId(), periodo.desde()).ifPresent(version -> {
            CuotaPlan cuota = new CuotaPlan(
                    afiliacion.getAfiliado(), afiliacion.getPlan(), periodo.desde(), periodo.hasta(), version.getPrecioMensual());
            cuota.marcarDeActivacion();
            cuotaRepository.save(cuota);
        });
    }

    /** Genera las cuotas del periodo vigente de todas las afiliaciones (tarea diaria). */
    @Transactional
    public void generarCuotasDeTodos(LocalDate hoy) {
        afiliacionRepository.findAllBy().forEach(afiliacion -> generarCuotas(afiliacion, hoy));
    }

    @Transactional
    public HistorialPagosResponse historial(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        // Si la tarea diaria aún no corrió, la cuota del periodo vigente se crea al consultar.
        afiliacionRepository.findByAfiliadoId(afiliadoId).ifPresent(afiliacion -> generarCuotas(afiliacion, LocalDate.now(clock)));

        List<CuotaPlan> cuotas = cuotaRepository.findAllByAfiliadoIdOrderByPeriodoDesdeDesc(afiliadoId);
        BigDecimal pagado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        for (CuotaPlan cuota : cuotas) {
            if (cuota.estaPagada()) {
                pagado = pagado.add(cuota.getValor());
            } else if (cuota.estaPendiente()) {
                pendiente = pendiente.add(cuota.getValor());
            }
        }
        LocalDate hoy = LocalDate.now(clock);
        return new HistorialPagosResponse(pagado, pendiente, cuotas.stream().map(cuota -> toResponse(cuota, hoy)).toList());
    }

    @Transactional
    public CuotaPlanResponse registrarPago(Long afiliadoId, Long cuotaId, RegistrarPagoRequest request) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        CuotaPlan cuota = cuotaRepository.findByIdAndAfiliadoIdForUpdate(cuotaId, afiliadoId)
                .orElseThrow(() -> new CuotaNotFoundException(cuotaId));
        if (!cuota.estaPendiente()) {
            throw new BusinessRuleException("La cuota del periodo que empieza el " + cuota.getPeriodoDesde()
                    + (cuota.estaPagada() ? " ya está pagada." : " está anulada y no se cobra."));
        }
        // El pago queda en el libro de pagos, que además salda la cuota y reactiva el plan si estaba suspendido.
        pagoService.registrarManualCuota(cuota, request.fechaPago(), request.medioPago());
        return toResponse(cuota, LocalDate.now(clock));
    }

    /** Primera cuota pendiente (la más antigua), si la hay. */
    @Transactional(readOnly = true)
    public Optional<CuotaPlanResponse> primeraPendiente(Long afiliadoId) {
        LocalDate hoy = LocalDate.now(clock);
        return cuotaRepository.findAllByAfiliadoIdAndEstadoOrderByPeriodoDesdeAsc(afiliadoId, EstadoCuota.PENDIENTE)
                .stream().findFirst().map(cuota -> toResponse(cuota, hoy));
    }

    private static CuotaPlanResponse toResponse(CuotaPlan cuota, LocalDate hoy) {
        return new CuotaPlanResponse(
                cuota.getId(),
                cuota.getPlan().getCodigo(),
                cuota.getPlan().getNombre(),
                cuota.getPeriodoDesde(),
                cuota.getPeriodoHasta(),
                cuota.getValor(),
                cuota.getEstado().name(),
                cuota.fechaLimitePago(),
                cuota.estaVencida(hoy),
                cuota.isDeActivacion(),
                cuota.getFechaPago(),
                cuota.getMedioPago() == null ? null : cuota.getMedioPago().name());
    }
}
