package com.isapet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Revisa una vez al día qué afiliados están por terminar su periodo y les envía el recordatorio. */
@Component
public class RecordatorioFinPeriodoJob {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioFinPeriodoJob.class);

    private final NotificacionService notificacionService;
    private final Clock clock;

    public RecordatorioFinPeriodoJob(NotificacionService notificacionService, Clock clock) {
        this.notificacionService = notificacionService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notificaciones.recordatorio-cron:0 0 8 * * *}")
    public void ejecutar() {
        int generados = notificacionService.recordarFinDePeriodo(LocalDate.now(clock));
        log.info("Recordatorios de fin de periodo generados: {}", generados);
    }
}
