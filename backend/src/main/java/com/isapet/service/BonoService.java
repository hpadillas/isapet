package com.isapet.service;

import com.isapet.dto.BonoResponse;
import com.isapet.dto.CompraBonoEnLineaRequest;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CotizacionBonoResponse;
import com.isapet.dto.PagoEnLineaResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Bono;
import com.isapet.entity.Mascota;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.entity.TarifaBono;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.MascotaNotFoundException;
import com.isapet.exception.ServicioNotFoundException;
import com.isapet.mapper.BonoPrestacionMapper;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.BonoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PrestacionRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BonoService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final MascotaRepository mascotaRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BonoRepository bonoRepository;
    private final PrestacionRepository prestacionRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final PagoService pagoService;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final BonoPrestacionMapper mapper;
    private final Clock clock;

    public BonoService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            MascotaRepository mascotaRepository,
            ServicioVeterinarioRepository servicioRepository,
            BonoRepository bonoRepository,
            PrestacionRepository prestacionRepository,
            CuotaPlanRepository cuotaRepository,
            PagoService pagoService,
            BeneficiosPlanResolver beneficiosResolver,
            BonoPrestacionMapper mapper,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.mascotaRepository = mascotaRepository;
        this.servicioRepository = servicioRepository;
        this.bonoRepository = bonoRepository;
        this.prestacionRepository = prestacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoService = pagoService;
        this.beneficiosResolver = beneficiosResolver;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BonoResponse> listar(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return bonoRepository.findAllByAfiliadoIdOrderByFechaCompraDesc(afiliadoId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CotizacionBonoResponse cotizar(Long afiliadoId, Long mascotaId, Long servicioId) {
        Elegibilidad elegibilidad = evaluar(afiliadoId, mascotaId, servicioId,
                afiliacionRepository.findByAfiliadoId(afiliadoId));
        TarifaBono tarifa = elegibilidad.tarifa();
        return new CotizacionBonoResponse(
                elegibilidad.mascota().getId(),
                elegibilidad.mascota().getNombre(),
                elegibilidad.servicio().getId(),
                elegibilidad.servicio().getNombre(),
                elegibilidad.afiliacion().getPlan().getCodigo(),
                tarifa.valorBase(),
                tarifa.porcentajeDescuento(),
                tarifa.valorAPagar(),
                tarifa.ahorro());
    }

    @Transactional
    public BonoResponse comprar(Long afiliadoId, CompraBonoRequest request) {
        // El bloqueo sobre la afiliación serializa la compra con el registro de atenciones incluidas.
        Elegibilidad elegibilidad = evaluar(afiliadoId, request.mascotaId(), request.servicioId(),
                afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId));
        Afiliacion afiliacion = elegibilidad.afiliacion();
        Bono bono = bonoRepository.saveAndFlush(new Bono(
                afiliacion.getAfiliado(),
                elegibilidad.mascota(),
                elegibilidad.servicio(),
                afiliacion.getPlan(),
                elegibilidad.tarifa(),
                OffsetDateTime.now(clock)));
        pagoService.registrarManualBono(bono, request.medioPago());
        return mapper.toResponse(bono);
    }

    /**
     * Compra pagada en línea: el bono queda pendiente de pago y se abre la sesión en la pasarela.
     * Solo se puede usar cuando la pasarela confirme el pago; si lo rechaza, el bono se anula.
     */
    @Transactional
    public PagoEnLineaResponse comprarEnLinea(Long afiliadoId, CompraBonoEnLineaRequest request) {
        Elegibilidad elegibilidad = evaluar(afiliadoId, request.mascotaId(), request.servicioId(),
                afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId));
        Afiliacion afiliacion = elegibilidad.afiliacion();
        Bono bono = bonoRepository.saveAndFlush(new Bono(
                afiliacion.getAfiliado(),
                elegibilidad.mascota(),
                elegibilidad.servicio(),
                afiliacion.getPlan(),
                elegibilidad.tarifa(),
                OffsetDateTime.now(clock),
                true));
        return pagoService.iniciarPagoBono(bono);
    }

    private Elegibilidad evaluar(
            Long afiliadoId,
            Long mascotaId,
            Long servicioId,
            Optional<Afiliacion> afiliacionEncontrada) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        Afiliacion afiliacion = afiliacionEncontrada.orElseThrow(() -> new BusinessRuleException(
                "El afiliado no tiene un plan de afiliación; asígnale uno antes de comprar bonos."));

        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        if (!mascota.getAfiliado().getId().equals(afiliadoId)) {
            throw new BusinessRuleException("La mascota con id " + mascotaId + " no pertenece al afiliado.");
        }
        if (!afiliacion.cubre(mascota)) {
            throw new BusinessRuleException(
                    "La mascota " + mascota.getNombre() + " no está cubierta por el plan del afiliado.");
        }

        ServicioVeterinario servicio = servicioRepository.findById(servicioId)
                .filter(ServicioVeterinario::isActivo)
                .orElseThrow(() -> new ServicioNotFoundException(servicioId));

        Long planId = afiliacion.getPlan().getId();
        VersionPlan version = beneficiosResolver.versionVigente(planId, LocalDate.now(clock))
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan del afiliado no tiene condiciones comerciales vigentes a la fecha."));

        if (afiliacion.isPendienteActivacion()) {
            throw new BusinessRuleException("El plan aún no está activo: se activa al pagar su primera cuota.");
        }
        if (cuotaRepository.planSuspendido(afiliadoId, LocalDate.now(clock))) {
            throw new BusinessRuleException("El plan está suspendido por falta de pago. "
                    + "Registra el pago de la cuota vencida para reactivarlo y poder comprar bonos.");
        }
        // Los bonos pagan atenciones adicionales: solo se venden cuando se agotan las incluidas del periodo.
        Integer limite = version.getLimiteAtenciones();
        if (limite == null) {
            throw new BusinessRuleException("El plan " + afiliacion.getPlan().getNombre()
                    + " incluye atenciones ilimitadas; no es necesario comprar bonos.");
        }
        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), LocalDate.now(clock));
        long usadas = prestacionRepository.contarIncluidasEnPlan(afiliadoId, periodo.desde(), periodo.hastaExclusiva());
        if (usadas < limite) {
            throw new BusinessRuleException(("Al afiliado le quedan %d de %d atenciones incluidas en su plan en el periodo "
                    + "actual (hasta el %s); los bonos se compran cuando se agoten.")
                    .formatted(limite - usadas, limite, periodo.hasta()));
        }

        TarifaBono tarifa = TarifaBono.calcular(
                servicio.getValorBase(),
                beneficiosResolver.porcentajeDescuento(planId, servicio.getId()));
        return new Elegibilidad(afiliacion, mascota, servicio, tarifa);
    }

    private record Elegibilidad(
            Afiliacion afiliacion,
            Mascota mascota,
            ServicioVeterinario servicio,
            TarifaBono tarifa) {
    }
}
