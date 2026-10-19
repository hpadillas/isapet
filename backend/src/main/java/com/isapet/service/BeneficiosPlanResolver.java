package com.isapet.service;

import com.isapet.dto.BeneficioServicioResponse;
import com.isapet.entity.BeneficioPlan;
import com.isapet.entity.Plan;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.entity.TarifaBono;
import com.isapet.entity.VersionPlan;
import com.isapet.repository.BeneficioPlanRepository;
import com.isapet.repository.VersionPlanRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Centraliza cómo se obtienen la versión comercial vigente y los descuentos de un plan,
 * para que simulación, consulta de plan y compra de bonos calculen igual.
 */
@Component
public class BeneficiosPlanResolver {

    private final BeneficioPlanRepository beneficioPlanRepository;
    private final VersionPlanRepository versionPlanRepository;

    public BeneficiosPlanResolver(
            BeneficioPlanRepository beneficioPlanRepository,
            VersionPlanRepository versionPlanRepository) {
        this.beneficioPlanRepository = beneficioPlanRepository;
        this.versionPlanRepository = versionPlanRepository;
    }

    public Optional<VersionPlan> versionVigente(Long planId, LocalDate fecha) {
        return versionPlanRepository.findFirstByPlanIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(planId, fecha);
    }

    /** Variante en memoria para cuando el plan ya trae sus versiones cargadas. */
    public Optional<VersionPlan> versionVigente(Plan plan, LocalDate fecha) {
        return plan.getVersiones().stream()
                .filter(version -> !version.getVigenteDesde().isAfter(fecha))
                .max(Comparator.comparing(VersionPlan::getVigenteDesde));
    }

    public int porcentajeDescuento(Long planId, Long servicioId) {
        return beneficioPlanRepository.findByPlanIdAndServicioId(planId, servicioId)
                .map(BeneficioPlan::getPorcentajeDescuento)
                .orElse(0);
    }

    public Map<Long, Map<Long, Integer>> descuentosPorPlan(Collection<Long> planIds) {
        return beneficioPlanRepository.findAllByPlanIdIn(planIds).stream()
                .collect(Collectors.groupingBy(
                        beneficio -> beneficio.getPlan().getId(),
                        Collectors.toMap(
                                beneficio -> beneficio.getServicio().getId(),
                                BeneficioPlan::getPorcentajeDescuento)));
    }

    public List<BeneficioServicioResponse> beneficios(
            List<ServicioVeterinario> servicios,
            Map<Long, Integer> descuentosPorServicio) {
        return servicios.stream()
                .map(servicio -> beneficio(servicio, descuentosPorServicio.getOrDefault(servicio.getId(), 0)))
                .toList();
    }

    public BeneficioServicioResponse beneficio(ServicioVeterinario servicio, int porcentajeDescuento) {
        TarifaBono tarifa = TarifaBono.calcular(servicio.getValorBase(), porcentajeDescuento);
        return new BeneficioServicioResponse(
                servicio.getId(),
                servicio.getCodigo(),
                servicio.getNombre(),
                servicio.getTipo().name(),
                tarifa.valorBase(),
                tarifa.porcentajeDescuento(),
                tarifa.valorAPagar());
    }
}
