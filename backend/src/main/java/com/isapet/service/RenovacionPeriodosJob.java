package com.isapet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Cada madrugada termina los planes cancelados, activa los cambios de plan programados, genera la cuota de
 * los periodos que empiezan y avisa las suspensiones por falta de pago. También corre al arrancar, por si
 * el servidor estuvo apagado a esa hora.
 */
@Component
public class RenovacionPeriodosJob {

    private static final Logger log = LoggerFactory.getLogger(RenovacionPeriodosJob.class);

    private final AfiliacionService afiliacionService;
    private final CuotaPlanService cuotaService;
    private final CancelacionPlanService cancelacionService;
    private final NotificacionService notificacionService;
    private final Clock clock;

    public RenovacionPeriodosJob(
            AfiliacionService afiliacionService,
            CuotaPlanService cuotaService,
            CancelacionPlanService cancelacionService,
            NotificacionService notificacionService,
            Clock clock) {
        this.afiliacionService = afiliacionService;
        this.cuotaService = cuotaService;
        this.cancelacionService = cancelacionService;
        this.notificacionService = notificacionService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${app.periodos.renovacion-cron:0 5 0 * * *}")
    public void ejecutar() {
        renovar(LocalDate.now(clock));
    }

    /** Ejecuta la renovación como si fuera {@code fecha}; la usa también el endpoint administrativo. */
    public Map<String, Integer> renovar(LocalDate fecha) {
        // Primero las cancelaciones, para no generar cuotas de planes que ya terminaron.
        int cancelados = cancelacionService.aplicarCancelacionesCumplidas(fecha);
        int cambios = afiliacionService.aplicarCambiosProgramados(fecha);
        cuotaService.generarCuotasDeTodos(fecha);
        int suspensiones = notificacionService.notificarSuspensiones(fecha);
        log.info("Renovación de periodos del {}: {} plan(es) terminado(s), {} cambio(s) de plan, {} aviso(s) de suspensión.",
                fecha, cancelados, cambios, suspensiones);
        return Map.of(
                "planesTerminados", cancelados,
                "cambiosDePlanActivados", cambios,
                "avisosDeSuspension", suspensiones);
    }
}
