package com.isapet.integration;

import com.isapet.dto.RegistrarPagoRequest;
import com.isapet.entity.MedioPago;
import com.isapet.service.CuotaPlanService;

import java.time.LocalDate;

/** El plan se activa al pagar su primera cuota: las pruebas que necesitan un plan activo la pagan. */
final class ActivacionPlan {

    private ActivacionPlan() {
    }

    static void pagarActivacion(CuotaPlanService cuotaService, Long afiliadoId) {
        cuotaService.historial(afiliadoId).cuotas().stream()
                .filter(cuota -> cuota.deActivacion() && cuota.estado().equals("PENDIENTE"))
                .findFirst()
                .ifPresent(cuota -> cuotaService.registrarPago(
                        afiliadoId, cuota.id(), new RegistrarPagoRequest(LocalDate.now(), MedioPago.EFECTIVO)));
    }
}
