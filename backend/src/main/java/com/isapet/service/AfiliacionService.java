package com.isapet.service;

import com.isapet.dto.AsignarPlanRequest;
import com.isapet.dto.BonoResponse;
import com.isapet.dto.CambioPlanProgramadoResponse;
import com.isapet.dto.MascotaCubiertaResponse;
import com.isapet.dto.PlanAfiliadoResponse;
import com.isapet.dto.PrestacionResponse;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.entity.Plan;
import com.isapet.entity.VersionPlan;
import com.isapet.exception.AfiliacionNotFoundException;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.PlanNotFoundException;
import com.isapet.mapper.BonoPrestacionMapper;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.BonoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.PrestacionRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AfiliacionService {

    private static final Logger log = LoggerFactory.getLogger(AfiliacionService.class);

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final PlanRepository planRepository;
    private final MascotaRepository mascotaRepository;
    private final BonoRepository bonoRepository;
    private final PrestacionRepository prestacionRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BeneficiosPlanResolver beneficiosResolver;
    private final BonoPrestacionMapper mapper;
    private final ApplicationEventPublisher eventos;
    private final CuotaPlanService cuotaService;
    private final CuotaPlanRepository cuotaRepository;
    private final Clock clock;

    public AfiliacionService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            PlanRepository planRepository,
            MascotaRepository mascotaRepository,
            BonoRepository bonoRepository,
            PrestacionRepository prestacionRepository,
            ServicioVeterinarioRepository servicioRepository,
            BeneficiosPlanResolver beneficiosResolver,
            BonoPrestacionMapper mapper,
            ApplicationEventPublisher eventos,
            CuotaPlanService cuotaService,
            CuotaPlanRepository cuotaRepository,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.planRepository = planRepository;
        this.mascotaRepository = mascotaRepository;
        this.bonoRepository = bonoRepository;
        this.prestacionRepository = prestacionRepository;
        this.servicioRepository = servicioRepository;
        this.beneficiosResolver = beneficiosResolver;
        this.mapper = mapper;
        this.eventos = eventos;
        this.cuotaService = cuotaService;
        this.cuotaRepository = cuotaRepository;
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
        List<Mascota> mascotas = mascotasDelAfiliado(afiliadoId, request.mascotaIds());
        LocalDate hoy = LocalDate.now(clock);

        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId).orElse(null);
        if (afiliacion == null) {
            // Primera afiliación: queda pendiente hasta que se pague la cuota de activación.
            validarPlan(plan, hoy, mascotas);
            Afiliacion nueva = new Afiliacion(afiliado, plan, hoy, mascotas);
            nueva.marcarPendienteDeActivacion();
            afiliacion = afiliacionRepository.save(nueva);
            afiliacionRepository.flush();
            cuotaService.generarCuotas(afiliacion, hoy);
        } else if (afiliacion.getPlan().getId().equals(plan.getId())) {
            // Mismo plan: solo cambian las mascotas cubiertas, de inmediato.
            validarMaximoMascotas(plan, versionVigente(plan, hoy), mascotas);
            afiliacion.actualizarMascotas(mascotas);
        } else {
            if (afiliacion.isPendienteActivacion()) {
                throw new BusinessRuleException("El plan " + afiliacion.getPlan().getNombre() + " aún no se ha activado. "
                        + "Cancélalo (se anula su cuota) y asigna el otro plan, o paga la activación y luego programa el cambio.");
            }
            if (afiliacion.tieneCancelacionProgramada()) {
                throw new BusinessRuleException("El plan tiene una cancelación programada para el " + afiliacion.getFechaFin()
                        + ". Deshaz la cancelación antes de cambiar de plan.");
            }
            // Otro plan: el actual sigue hasta el final del periodo ya iniciado y el nuevo empieza al día siguiente.
            LocalDate fechaCambio = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), hoy).hastaExclusiva();
            validarPlan(plan, fechaCambio, mascotas);
            afiliacion.programarCambio(plan, fechaCambio, mascotas);
        }
        afiliacionRepository.flush();
        return response(afiliacion);
    }

    @Transactional
    public PlanAfiliadoResponse cancelarCambioProgramado(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        Afiliacion afiliacion = afiliacionRepository.findByAfiliadoIdForUpdate(afiliadoId)
                .orElseThrow(() -> new AfiliacionNotFoundException(afiliadoId));
        if (!afiliacion.tieneCambioProgramado()) {
            throw new BusinessRuleException("El afiliado no tiene un cambio de plan programado.");
        }
        afiliacion.cancelarCambioProgramado();
        afiliacionRepository.flush();
        return response(afiliacion);
    }

    /**
     * Activa los cambios de plan cuya fecha ya llegó. Si el plan programado se desactivó o no tiene
     * condiciones vigentes en esa fecha, el cambio se descarta y el afiliado conserva su plan.
     *
     * @return cantidad de cambios activados.
     */
    @Transactional
    public int aplicarCambiosProgramados(LocalDate hoy) {
        int aplicados = 0;
        for (Afiliacion afiliacion : afiliacionRepository.findConCambioProgramadoHastaForUpdate(hoy)) {
            Plan nuevo = afiliacion.getPlanProgramado();
            boolean vigente = nuevo.isActivo()
                    && beneficiosResolver.versionVigente(nuevo.getId(), afiliacion.getFechaCambioProgramado()).isPresent();
            if (!vigente) {
                log.warn("Se descarta el cambio al plan {} del afiliado {}: el plan ya no está disponible.",
                        nuevo.getCodigo(), afiliacion.getAfiliado().getId());
                afiliacion.cancelarCambioProgramado();
                continue;
            }
            afiliacion.aplicarCambioProgramado();
            afiliacionRepository.flush();
            cuotaService.generarCuotas(afiliacion, hoy);
            eventos.publishEvent(new EventoAfiliado.PlanActivado(afiliacion.getAfiliado().getId()));
            aplicados++;
        }
        return aplicados;
    }

    private void validarPlan(Plan plan, LocalDate fecha, List<Mascota> mascotas) {
        if (!plan.isActivo()) {
            throw new BusinessRuleException("El plan " + plan.getNombre() + " está inactivo y no admite afiliaciones.");
        }
        validarMaximoMascotas(plan, versionVigente(plan, fecha), mascotas);
    }

    private VersionPlan versionVigente(Plan plan, LocalDate fecha) {
        return beneficiosResolver.versionVigente(plan.getId(), fecha)
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan " + plan.getNombre() + " no tiene condiciones comerciales vigentes al " + fecha + "."));
    }

    private static void validarMaximoMascotas(Plan plan, VersionPlan version, List<Mascota> mascotas) {
        if (mascotas.size() > version.getMaximoMascotas()) {
            throw new BusinessRuleException("El plan %s cubre máximo %d mascota(s); seleccionaste %d.".formatted(
                    plan.getNombre(), version.getMaximoMascotas(), mascotas.size()));
        }
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
        LocalDate hoy = LocalDate.now(clock);
        VersionPlan version = beneficiosResolver.versionVigente(plan.getId(), hoy)
                .orElseThrow(() -> new BusinessRuleException(
                        "El plan " + plan.getNombre() + " no tiene condiciones comerciales vigentes a la fecha."));

        Map<Long, Integer> descuentos = beneficiosResolver.descuentosPorPlan(List.of(plan.getId()))
                .getOrDefault(plan.getId(), Map.of());
        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), hoy);
        List<PrestacionResponse> incluidas = prestacionRepository
                .buscarIncluidasEnPlan(afiliadoId, periodo.desde(), periodo.hastaExclusiva()).stream()
                .map(mapper::toResponse)
                .toList();
        List<BonoResponse> bonos = bonoRepository
                .findAllByAfiliadoIdAndFechaCompraGreaterThanEqualAndFechaCompraLessThanOrderByFechaCompraDesc(
                        afiliadoId, periodo.inicio(clock.getZone()), periodo.fin(clock.getZone()))
                .stream()
                .map(mapper::toResponse)
                .toList();
        Integer limite = version.getLimiteAtenciones();

        List<MascotaCubiertaResponse> mascotas = mascotasResponse(afiliacion.getMascotasCubiertas());

        CambioPlanProgramadoResponse cambio = null;
        if (afiliacion.tieneCambioProgramado()) {
            Plan nuevo = afiliacion.getPlanProgramado();
            cambio = new CambioPlanProgramadoResponse(
                    nuevo.getId(),
                    nuevo.getCodigo(),
                    nuevo.getNombre(),
                    afiliacion.getFechaCambioProgramado(),
                    beneficiosResolver.versionVigente(nuevo.getId(), afiliacion.getFechaCambioProgramado())
                            .map(VersionPlan::getPrecioMensual)
                            .orElse(null),
                    mascotasResponse(afiliacion.getMascotasProgramadas()));
        }

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
                periodo.desde(),
                periodo.hasta(),
                incluidas.size(),
                limite == null ? null : Math.max(0, limite - incluidas.size()),
                incluidas,
                bonos,
                cambio,
                afiliacion.isPendienteActivacion() ? "PENDIENTE_ACTIVACION"
                        : cuotaRepository.planSuspendido(afiliadoId, hoy) ? "SUSPENDIDO" : "ACTIVO",
                afiliacion.getFechaFin(),
                cuotaService.primeraPendiente(afiliadoId).orElse(null));
    }

    private static List<MascotaCubiertaResponse> mascotasResponse(Collection<Mascota> mascotas) {
        return mascotas.stream()
                .sorted(Comparator.comparing(Mascota::getId))
                .map(mascota -> new MascotaCubiertaResponse(mascota.getId(), mascota.getNombre(), mascota.getEspecie()))
                .toList();
    }
}
