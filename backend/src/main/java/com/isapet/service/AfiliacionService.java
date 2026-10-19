package com.isapet.service;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.MascotaCubiertaResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.Plan;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.AfiliacionNotFoundException;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.PlanNotFoundException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.BonoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AfiliacionService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final PlanRepository planRepository;
    private final MascotaRepository mascotaRepository;
    private final BonoRepository bonoRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final Clock clock;

    public AfiliacionService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            PlanRepository planRepository,
            MascotaRepository mascotaRepository,
            BonoRepository bonoRepository,
            ServicioVeterinarioRepository servicioRepository,
            BeneficiosPlanResolver beneficiosResolver,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.planRepository = planRepository;
        this.mascotaRepository = mascotaRepository;
        this.bonoRepository = bonoRepository;
        this.servicioRepository = servicioRepository;
        this.beneficiosResolver = beneficiosResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanAfiliadoResponse obtenerPlan(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoId(afiliadoId)
                .orElseThrow(() -> new AfiliacionNotFoundException(afiliadoId));
        return response(afiliacion);
    }

    @Transactional
    public PlanAfiliadoResponse asignarPlan(Long afiliadoId, AsignarPlanRequest request) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new PlanNotFoundException(request.planId()));
        if (!plan.isActivo()) {
            throw new BusinessRuleException("El plan " + plan.getNombre() + " está inactivo y no admite afiliaciones.");
        }
        LocalDate hoy = LocalDate.now(clock);
        VersionPlan version = beneficiosResolver.versionVigente(plan.getId(), hoy)
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan " + plan.getNombre() + " no tiene condiciones comerciales vigentes a la fecha."));

        List<Mascota> mascotas = mascotasDelAfiliado(afiliadoId, request.mascotaIds());
        if (mascotas.size() > version.getMaximoMascotas()) {
            throw new BusinessRuleException("El plan %s cubre máximo %d mascota(s); seleccionaste %d.".formatted(
                    plan.getNombre(), version.getMaximoMascotas(), mascotas.size()));
        }

        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId)
                .map(existente -> {
                    existente.cambiarPlan(plan, hoy, mascotas);
                    return existente;
                })
                .orElseGet(() -> afiliacionRepository.save(new Afiliacion(afiliado, plan, hoy, mascotas)));
        afiliacionRepository.flush();
        return response(afiliacion);
    }

    private List<Mascota> mascotasDelAfiliado(Long afiliadoId, List<Long> mascotaIds) {
        Set<Long> idsUnicos = new LinkedHashSet<>(mascotaIds);
        List<Mascota> mascotas = mascotaRepository.findAllById(idsUnicos);
        for (Long mascotaId : idsUnicos) {
            boolean perteneceAlAfiliado = mascotas.stream().anyMatch(mascota ->
                    mascota.getId().equals(mascotaId) && mascota.getAfiliado().getId().equals(afiliadoId));
            if (!perteneceAlAfiliado) {
                throw new BusinessRuleException("La mascota con id " + mascotaId + " no pertenece al afiliado.");
            }
        }
        return mascotas;
    }

    private PlanAfiliadoResponse response(Afiliacion afiliacion) {
        Plan plan = afiliacion.getPlan();
        Long afiliadoId = afiliacion.getAfiliado().getId();
        VersionPlan version = beneficiosResolver.versionVigente(plan.getId(), LocalDate.now(clock))
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan " + plan.getNombre() + " no tiene condiciones comerciales vigentes a la fecha."));

        Map<Long, Integer> descuentos = beneficiosResolver.descuentosPorPlan(List.of(plan.getId()))
                .getOrDefault(plan.getId(), Map.of());
        PeriodoMensual mes = PeriodoMensual.actual(clock);
        long bonosMes = bonoRepository.countByAfiliadoIdAndFechaCompraGreaterThanEqualAndFechaCompraLessThan(
                afiliadoId, mes.desde(), mes.hastaExclusiva());
        Integer limite = version.getLimiteAtenciones();

        List<MascotaCubiertaResponse> mascotas = afiliacion.getMascotasCubiertas().stream()
                .sorted(Comparator.comparing(Mascota::getId))
                .map(mascota -> new MascotaCubiertaResponse(mascota.getId(), mascota.getNombre(), mascota.getEspecie()))
                .toList();

        return new PlanAfiliadoResponse(
                afiliadoId,
                plan.getId(),
                plan.getCodigo(),
                plan.getNombre(),
                plan.isActivo(),
                afiliacion.getFechaInicio(),
                version.getPrecioMensual(),
                version.getMaximoMascotas(),
                limite,
                limite == null,
                version.getVigenteDesde(),
                mascotas,
                beneficiosResolver.beneficios(servicioRepository.findAllByActivoTrueOrderByTipoAscValorBaseAsc(), descuentos),
                bonosMes,
                limite == null ? null : (int) Math.max(0, limite - bonosMes));
    }
}
