package com.isapet.service;

import com.isapet.dto.BeneficioServicioResponse;
import com.isapet.dto.OpcionPlanSimuladaResponse;
import com.isapet.dto.ServicioEstimadoRequest;
import com.isapet.dto.ServicioVeterinarioResponse;
import com.isapet.dto.SimulacionPlanRequest;
import com.isapet.dto.SimulacionPlanResponse;
import com.isapet.entity.Plan;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.ServicioNotFoundException;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SimulacionPlanService {

    private final PlanRepository planRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final Clock clock;

    public SimulacionPlanService(
            PlanRepository planRepository,
            ServicioVeterinarioRepository servicioRepository,
            BeneficiosPlanResolver beneficiosResolver,
            Clock clock) {
        this.planRepository = planRepository;
        this.servicioRepository = servicioRepository;
        this.beneficiosResolver = beneficiosResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServicioVeterinarioResponse> listarServicios() {
        return servicioRepository.findAllByActivoTrueOrderByTipoAscValorBaseAsc().stream()
                .map(servicio -> new ServicioVeterinarioResponse(
                        servicio.getId(),
                        servicio.getCodigo(),
                        servicio.getNombre(),
                        servicio.getTipo().name(),
                        servicio.getValorBase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SimulacionPlanResponse simular(SimulacionPlanRequest request) {
        LocalDate hoy = LocalDate.now(clock);
        List<ServicioVeterinario> servicios = servicioRepository.findAllByActivoTrueOrderByTipoAscValorBaseAsc();
        Map<Long, ServicioVeterinario> serviciosPorId = servicios.stream()
                .collect(Collectors.toMap(ServicioVeterinario::getId, Function.identity()));
        Map<Long, Integer> cantidades = cantidadesPorServicio(request.serviciosOVacio(), serviciosPorId);

        List<Plan> planes = planRepository.findAllByOrderByCodigoAsc().stream()
                .filter(Plan::isActivo)
                .toList();
        Map<Long, Map<Long, Integer>> descuentos = beneficiosResolver.descuentosPorPlan(
                planes.stream().map(Plan::getId).toList());

        List<Calculo> calculos = new ArrayList<>();
        for (Plan plan : planes) {
            beneficiosResolver.versionVigente(plan, hoy).ifPresent(version -> {
                List<BeneficioServicioResponse> beneficios = beneficiosResolver.beneficios(
                        servicios, descuentos.getOrDefault(plan.getId(), Map.of()));
                calculos.add(calcular(plan, version, beneficios, cantidades, request.cantidadMascotas()));
            });
        }

        Long recomendadoId = calculos.stream()
                .filter(Calculo::aplica)
                .min(Comparator.comparing(Calculo::valorMensualEstimado)
                        .thenComparing(calculo -> calculo.version().getPrecioMensual()))
                .map(calculo -> calculo.plan().getId())
                .orElse(null);

        List<OpcionPlanSimuladaResponse> opciones = calculos.stream()
                .sorted(Comparator.comparing(calculo -> calculo.version().getPrecioMensual()))
                .map(calculo -> calculo.toResponse(calculo.plan().getId().equals(recomendadoId)))
                .toList();

        return new SimulacionPlanResponse(hoy, request.cantidadMascotas(), opciones);
    }

    private Map<Long, Integer> cantidadesPorServicio(
            List<ServicioEstimadoRequest> solicitados,
            Map<Long, ServicioVeterinario> serviciosPorId) {
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ServicioEstimadoRequest solicitado : solicitados) {
            if (!serviciosPorId.containsKey(solicitado.servicioId())) {
                throw new ServicioNotFoundException(solicitado.servicioId());
            }
            cantidades.merge(solicitado.servicioId(), solicitado.cantidadMensual(), Integer::sum);
        }
        return cantidades;
    }

    /**
     * Las atenciones incluidas en el plan no tienen costo y se asignan primero a los servicios de mayor
     * valor, que es como el afiliado las aprovecharía; las que las superan se pagan con bonos al valor
     * con descuento del plan.
     */
    private Calculo calcular(
            Plan plan,
            VersionPlan version,
            List<BeneficioServicioResponse> beneficios,
            Map<Long, Integer> cantidades,
            int cantidadMascotas) {
        List<BeneficioServicioResponse> unidades = new ArrayList<>();
        BigDecimal costoSinPlan = BigDecimal.ZERO;
        for (BeneficioServicioResponse beneficio : beneficios) {
            int cantidad = cantidades.getOrDefault(beneficio.servicioId(), 0);
            for (int i = 0; i < cantidad; i++) {
                unidades.add(beneficio);
            }
            costoSinPlan = costoSinPlan.add(beneficio.valorBase().multiply(BigDecimal.valueOf(cantidad)));
        }
        unidades.sort(Comparator.comparing(BeneficioServicioResponse::valorConPlan).reversed());

        Integer limite = version.getLimiteAtenciones();
        int incluidas = limite == null ? unidades.size() : Math.min(limite, unidades.size());
        BigDecimal costoConPlan = BigDecimal.ZERO;
        for (BeneficioServicioResponse adicional : unidades.subList(incluidas, unidades.size())) {
            costoConPlan = costoConPlan.add(adicional.valorConPlan());
        }

        boolean aplica = cantidadMascotas <= version.getMaximoMascotas();
        String motivo = aplica ? null : "El plan cubre máximo %d %s.".formatted(
                version.getMaximoMascotas(), version.getMaximoMascotas() == 1 ? "mascota" : "mascotas");
        BigDecimal valorMensual = version.getPrecioMensual().add(costoConPlan);
        return new Calculo(plan, version, beneficios, aplica, motivo, costoSinPlan, costoConPlan, valorMensual);
    }

    private record Calculo(
            Plan plan,
            VersionPlan version,
            List<BeneficioServicioResponse> beneficios,
            boolean aplica,
            String motivoNoAplica,
            BigDecimal costoServiciosSinPlan,
            BigDecimal costoServiciosConPlan,
            BigDecimal valorMensualEstimado) {

        OpcionPlanSimuladaResponse toResponse(boolean recomendado) {
            return new OpcionPlanSimuladaResponse(
                    plan.getId(),
                    plan.getCodigo(),
                    plan.getNombre(),
                    version.getPrecioMensual(),
                    version.getMaximoMascotas(),
                    version.getLimiteAtenciones(),
                    version.getLimiteAtenciones() == null,
                    aplica,
                    motivoNoAplica,
                    beneficios,
                    costoServiciosSinPlan,
                    costoServiciosConPlan,
                    valorMensualEstimado,
                    costoServiciosSinPlan.subtract(valorMensualEstimado),
                    recomendado);
        }
    }
}
