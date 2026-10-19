package com.isapet.service;

import com.isapet.dto.BonoResponse;
import com.isapet.dto.CompraBonoRequest;
import com.isapet.dto.CotizacionBonoResponse;
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
import com.isapet.repository.MascotaRepository;
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
    private final BeneficiosPlanResolver beneficiosResolver;
    private final BonoPrestacionMapper mapper;
    private final Clock clock;

    public BonoService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            MascotaRepository mascotaRepository,
            ServicioVeterinarioRepository servicioRepository,
            BonoRepository bonoRepository,
            BeneficiosPlanResolver beneficiosResolver,
            BonoPrestacionMapper mapper,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.mascotaRepository = mascotaRepository;
        this.servicioRepository = servicioRepository;
        this.bonoRepository = bonoRepository;
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
                tarifa.ahorro(),
                elegibilidad.bonosDisponiblesMes());
    }

    @Transactional
    public BonoResponse comprar(Long afiliadoId, CompraBonoRequest request) {
        // El bloqueo sobre la afiliación serializa compras concurrentes y protege el límite mensual.
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
        return mapper.toResponse(bono);
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

        Integer disponibles = null;
        if (version.getLimiteAtenciones() != null) {
            PeriodoMensual mes = PeriodoMensual.actual(clock);
            long compradosMes = bonoRepository.countByAfiliadoIdAndFechaCompraGreaterThanEqualAndFechaCompraLessThan(
                    afiliadoId, mes.desde(), mes.hastaExclusiva());
            disponibles = (int) Math.max(0, version.getLimiteAtenciones() - compradosMes);
            if (disponibles == 0) {
                throw new BusinessRuleException("El afiliado alcanzó el límite de %d atenciones de su plan este mes."
                        .formatted(version.getLimiteAtenciones()));
            }
        }

        TarifaBono tarifa = TarifaBono.calcular(
                servicio.getValorBase(),
                beneficiosResolver.porcentajeDescuento(planId, servicio.getId()));
        return new Elegibilidad(afiliacion, mascota, servicio, tarifa, disponibles);
    }

    private record Elegibilidad(
            Afiliacion afiliacion,
            Mascota mascota,
            ServicioVeterinario servicio,
            TarifaBono tarifa,
            Integer bonosDisponiblesMes) {
    }
}
