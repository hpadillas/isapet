package com.isapet.service;

import com.isapet.entity.Pago;

/**
 * Integración con la pasarela de pagos. Para cambiar a una pasarela real (Wompi, PayU, Mercado Pago)
 * se implementa esta interfaz con su API y se configura el mismo secreto que firma sus notificaciones
 * al webhook ({@code /api/v1/pasarela/webhook}).
 */
public interface PasarelaPagos {

    /** Abre la sesión de pago en la pasarela y devuelve la URL donde se paga. */
    String urlDePago(Pago pago);
}
