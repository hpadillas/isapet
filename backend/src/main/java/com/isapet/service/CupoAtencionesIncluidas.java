package com.isapet.service;

import com.isapet.entity.Afiliacion;
import com.isapet.repository.PrestacionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Única fuente de la regla del cupo de atenciones incluidas: en el periodo que contiene una fecha, cuántas
 * atenciones cubre el plan según su versión comercial vigente y cuántas ya se usaron. La venta de bonos y
 * el registro de prestaciones deciden con este mismo cálculo, para que nunca se contradigan.
 */
@Component
public class CupoAtencionesIncluidas {

    private final BeneficiosPlanResolver beneficiosResolver;
    private final PrestacionRepository prestacionRepository;

    public CupoAtencionesIncluidas(BeneficiosPlanResolver beneficiosResolver, PrestacionRepository prestacionRepository) {
        this.beneficiosResolver = beneficiosResolver;
        this.prestacionRepository = prestacionRepository;
    }

    /** Vacío si el plan no tiene condiciones comerciales vigentes en {@code fecha}. */
    public Optional<UsoAtencionesIncluidas> consultar(Afiliacion afiliacion, LocalDate fecha) {
        PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), fecha);
        return beneficiosResolver.versionVigente(afiliacion.getPlan().getId(), fecha).map(version -> {
            Integer limite = version.getLimiteAtenciones();
            long usadas = limite == null ? 0 : prestacionRepository.contarIncluidasEnPlan(
                    afiliacion.getAfiliado().getId(), periodo.desde(), periodo.hastaExclusiva());
            return new UsoAtencionesIncluidas(periodo, limite, usadas);
        });
    }
}
