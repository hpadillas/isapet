package com.isapet.service;

import com.isapet.service.EventoAfiliado.AfiliadoRegistrado;
import com.isapet.service.EventoAfiliado.AtencionesIncluidasAgotadas;
import com.isapet.service.EventoAfiliado.PlanActivado;
import com.isapet.service.EventoAfiliado.PlanCancelado;
import com.isapet.service.EventoAfiliado.PlanReactivado;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía las notificaciones solo después del commit y en otro hilo: un correo lento o fallido no
 * retrasa ni revierte la operación del administrador.
 */
@Component
public class NotificacionListener {

    private final NotificacionService notificacionService;

    public NotificacionListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @Async
    @TransactionalEventListener
    public void alRegistrarAfiliado(AfiliadoRegistrado evento) {
        notificacionService.bienvenida(evento.afiliadoId());
    }

    @Async
    @TransactionalEventListener
    public void alActivarPlan(PlanActivado evento) {
        notificacionService.planActivado(evento.afiliadoId());
    }

    @Async
    @TransactionalEventListener
    public void alCancelarPlan(PlanCancelado evento) {
        notificacionService.planCancelado(evento.afiliadoId(), evento.plan(), evento.ultimoDia(), evento.inmediata());
    }

    @Async
    @TransactionalEventListener
    public void alReactivarPlan(PlanReactivado evento) {
        notificacionService.planReactivado(evento.afiliadoId());
    }

    @Async
    @TransactionalEventListener
    public void alAgotarAtencionesIncluidas(AtencionesIncluidasAgotadas evento) {
        notificacionService.atencionesAgotadas(evento.afiliadoId(), evento.periodoDesde());
    }
}
