package com.isapet.service;

import com.isapet.entity.Pago;
import org.springframework.stereotype.Component;

/**
 * Pasarela de pruebas: la "página de pago" es una pantalla de ISAPET que permite aprobar o rechazar
 * el pago, y el resultado llega por el mismo webhook firmado que usaría una pasarela real.
 * No pide ni recibe datos de tarjeta.
 */
@Component
public class PasarelaSimulada implements PasarelaPagos {

    @Override
    public String urlDePago(Pago pago) {
        return "/pasarela/checkout/" + pago.getReferencia();
    }
}
