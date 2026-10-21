package com.isapet.service;

import com.isapet.dto.ResumenInicioResponse;
import com.isapet.dto.ResumenInicioResponse.AfiliadoReciente;
import com.isapet.dto.ResumenInicioResponse.Alerta;
import com.isapet.entity.Afiliacion;
import com.isapet.entity.Afiliado;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoCuota;
import com.isapet.entity.Plan;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.PlanRepository;
import com.isapet.repository.PrestacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reúne en una consulta lo que el administrador necesita ver al entrar. */
@Service
public class ResumenInicioService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final PlanRepository planRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final PrestacionRepository prestacionRepository;
    private final Clock clock;

    public ResumenInicioService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            PlanRepository planRepository,
            CuotaPlanRepository cuotaRepository,
            PrestacionRepository prestacionRepository,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.planRepository = planRepository;
        this.cuotaRepository = cuotaRepository;
        this.prestacionRepository = prestacionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ResumenInicioResponse resumen() {
        LocalDate hoy = LocalDate.now(clock);
        List<Afiliacion> afiliaciones = afiliacionRepository.findAllBy();
        List<CuotaPlan> pendientes = cuotaRepository.findAllByEstadoAndPeriodoDesdeLessThan(EstadoCuota.PENDIENTE, hoy.plusDays(1));

        List<Alerta> alertas = new ArrayList<>();
        Set<Long> suspendidos = new HashSet<>();
        BigDecimal valorPendiente = BigDecimal.ZERO;
        for (CuotaPlan cuota : pendientes) {
            valorPendiente = valorPendiente.add(cuota.getValor());
            Afiliado afiliado = cuota.getAfiliado();
            if (cuota.isDeActivacion()) {
                alertas.add(new Alerta("PENDIENTE_ACTIVACION", afiliado.getId(), nombre(afiliado),
                        "Plan %s asignado; se activa al pagar la primera cuota de %s.".formatted(
                                cuota.getPlan().getNombre(), pesos(cuota.getValor())),
                        cuota.getPeriodoDesde()));
            } else if (cuota.estaVencida(hoy)) {
                if (suspendidos.add(afiliado.getId())) {
                    alertas.add(new Alerta("SUSPENDIDO", afiliado.getId(), nombre(afiliado),
                            "Plan suspendido: la cuota de %s venció el %s.".formatted(
                                    pesos(cuota.getValor()), cuota.fechaLimitePago().format(FECHA)),
                            cuota.fechaLimitePago()));
                }
            } else {
                alertas.add(new Alerta("CUOTA_POR_VENCER", afiliado.getId(), nombre(afiliado),
                        "Cuota de %s pendiente; vence el %s.".formatted(
                                pesos(cuota.getValor()), cuota.fechaLimitePago().format(FECHA)),
                        cuota.fechaLimitePago()));
            }
        }
        for (Afiliacion afiliacion : afiliaciones) {
            Afiliado afiliado = afiliacion.getAfiliado();
            if (afiliacion.tieneCambioProgramado()) {
                alertas.add(new Alerta("CAMBIO_PROGRAMADO", afiliado.getId(), nombre(afiliado),
                        "Pasa del plan %s al %s el %s.".formatted(afiliacion.getPlan().getNombre(),
                                afiliacion.getPlanProgramado().getNombre(), afiliacion.getFechaCambioProgramado().format(FECHA)),
                        afiliacion.getFechaCambioProgramado()));
            }
            if (afiliacion.tieneCancelacionProgramada()) {
                alertas.add(new Alerta("CANCELACION_PROGRAMADA", afiliado.getId(), nombre(afiliado),
                        "Canceló el plan %s; lo conserva hasta el %s.".formatted(
                                afiliacion.getPlan().getNombre(), afiliacion.getFechaFin().format(FECHA)),
                        afiliacion.getFechaFin()));
            }
        }
        // Primero lo más urgente (suspensiones), luego por fecha.
        alertas.sort(Comparator.comparing((Alerta alerta) -> !alerta.tipo().equals("SUSPENDIDO"))
                .thenComparing(Alerta::fecha));

        LocalDate inicioMes = hoy.withDayOfMonth(1);
        return new ResumenInicioResponse(
                hoy,
                afiliadoRepository.count(),
                afiliaciones.size(),
                planRepository.findAllByOrderByCodigoAsc().stream().filter(Plan::isActivo).count(),
                pendientes.size(),
                valorPendiente,
                suspendidos.size(),
                prestacionRepository.countByFechaGreaterThanEqualAndFechaLessThan(inicioMes, inicioMes.plusMonths(1)),
                alertas,
                ultimosAfiliados());
    }

    private List<AfiliadoReciente> ultimosAfiliados() {
        return afiliadoRepository.findTop5ByOrderByIdDesc().stream()
                .map(afiliado -> new AfiliadoReciente(
                        afiliado.getId(),
                        nombre(afiliado),
                        afiliado.getNumeroIdentificacion(),
                        afiliacionRepository.findByAfiliadoId(afiliado.getId())
                                .map(afiliacion -> afiliacion.getPlan().getNombre())
                                .orElse(null)))
                .toList();
    }

    private static String nombre(Afiliado afiliado) {
        return afiliado.getNombres() + " " + afiliado.getApellidos();
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        formato.setMaximumFractionDigits(0);
        return formato.format(valor);
    }
}
