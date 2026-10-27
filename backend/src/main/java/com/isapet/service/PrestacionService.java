package com.isapet.service;

import com.isapet.dto.PrestacionResponse;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Bono;
import com.isapet.entity.CoberturaPrestacion;
import com.isapet.entity.Mascota;
import com.isapet.entity.Prestacion;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BonoNotFoundException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PrestacionService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final MascotaRepository mascotaRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BonoRepository bonoRepository;
    private final PrestacionRepository prestacionRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final CupoAtencionesIncluidas cupoAtenciones;
    private final BonoPrestacionMapper mapper;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public PrestacionService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            MascotaRepository mascotaRepository,
            ServicioVeterinarioRepository servicioRepository,
            BonoRepository bonoRepository,
            PrestacionRepository prestacionRepository,
            CuotaPlanRepository cuotaRepository,
            CupoAtencionesIncluidas cupoAtenciones,
            BonoPrestacionMapper mapper,
            ApplicationEventPublisher eventos,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.mascotaRepository = mascotaRepository;
        this.servicioRepository = servicioRepository;
        this.bonoRepository = bonoRepository;
        this.prestacionRepository = prestacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.cupoAtenciones = cupoAtenciones;
        this.mapper = mapper;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PrestacionResponse> listar(Long afiliadoId, Long mascotaId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        if (mascotaId != null) {
            mascotaDelAfiliado(afiliadoId, mascotaId);
        }
        return prestacionRepository.buscarPorAfiliado(afiliadoId, mascotaId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public PrestacionResponse registrar(Long afiliadoId, RegistrarPrestacionRequest request) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        Mascota mascota = mascotaDelAfiliado(afiliadoId, request.mascotaId());
        ServicioVeterinario servicio = servicioRepository.findById(request.servicioId())
                .orElseThrow(() -> new ServicioNotFoundException(request.servicioId()));

        Bono bono = null;
        CoberturaPrestacion cobertura;
        if (request.bonoId() != null) {
            bono = bonoRepository.findByIdForUpdate(request.bonoId())
                    .orElseThrow(() -> new BonoNotFoundException(request.bonoId()));
            validarBono(bono, afiliadoId, mascota, servicio, request);
            bono.marcarUsado();
            cobertura = CoberturaPrestacion.BONO;
        } else {
            Optional<CupoIncluido> cupo = cupoIncluidoDisponible(afiliadoId, mascota, request.fecha());
            cobertura = cupo.isPresent() ? CoberturaPrestacion.PLAN : CoberturaPrestacion.PARTICULAR;
            cupo.filter(CupoIncluido::esElUltimo).ifPresent(ultimo -> eventos.publishEvent(
                    new EventoAfiliado.AtencionesIncluidasAgotadas(afiliadoId, ultimo.periodo().desde())));
        }

        String observaciones = request.observaciones() == null || request.observaciones().isBlank()
                ? null
                : request.observaciones().trim();
        Prestacion prestacion = prestacionRepository.saveAndFlush(new Prestacion(
                mascota,
                servicio,
                bono,
                cobertura,
                request.fecha(),
                request.prestador().trim(),
                observaciones,
                OffsetDateTime.now(clock)));
        return mapper.toResponse(prestacion);
    }

    /**
     * Sin bono, la atención consume una de las incluidas en el plan si la mascota está cubierta, la fecha
     * no es anterior a la afiliación y aún quedan atenciones en su periodo; si no, es particular (valor pleno).
     * El bloqueo sobre la afiliación evita que registros simultáneos superen el límite del periodo.
     */
    private Optional<CupoIncluido> cupoIncluidoDisponible(Long afiliadoId, Mascota mascota, LocalDate fecha) {
        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId).orElse(null);
        if (afiliacion == null || afiliacion.isPendienteActivacion()
                || !afiliacion.cubre(mascota) || fecha.isBefore(afiliacion.getFechaInicio())) {
            return Optional.empty();
        }
        // Sin atenciones incluidas después del último día de un plan cancelado ni mientras esté suspendido.
        if ((afiliacion.tieneCancelacionProgramada() && fecha.isAfter(afiliacion.getFechaFin()))
                || cuotaRepository.planSuspendido(afiliadoId, fecha)) {
            return Optional.empty();
        }
        return cupoAtenciones.consultar(afiliacion, fecha)
                .filter(UsoAtencionesIncluidas::quedanDisponibles)
                .map(uso -> new CupoIncluido(uso.periodo(), uso.siguienteEsLaUltima()));
    }

    private record CupoIncluido(PeriodoAfiliacion periodo, boolean esElUltimo) {
    }

    private void validarBono(
            Bono bono,
            Long afiliadoId,
            Mascota mascota,
            ServicioVeterinario servicio,
            RegistrarPrestacionRequest request) {
        if (!bono.getAfiliado().getId().equals(afiliadoId)) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " no pertenece al afiliado.");
        }
        if (!bono.estaDisponible()) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + switch (bono.getEstado()) {
                case PENDIENTE_PAGO -> " aún no se ha pagado: se podrá usar cuando la pasarela confirme el pago.";
                case ANULADO -> " fue anulado porque su pago en línea fue rechazado.";
                default -> " ya fue utilizado.";
            });
        }
        if (!bono.getMascota().getId().equals(mascota.getId())) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " fue comprado para otra mascota.");
        }
        if (!bono.getServicio().getId().equals(servicio.getId())) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " corresponde a otro servicio.");
        }
        if (request.fecha().isBefore(bono.getFechaCompra().atZoneSameInstant(clock.getZone()).toLocalDate())) {
            throw new BusinessRuleException("La prestación no puede ser anterior a la compra del bono.");
        }
    }

    private Mascota mascotaDelAfiliado(Long afiliadoId, Long mascotaId) {
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        if (!mascota.getAfiliado().getId().equals(afiliadoId)) {
            throw new BusinessRuleException("La mascota con id " + mascotaId + " no pertenece al afiliado.");
        }
        return mascota;
    }
}
