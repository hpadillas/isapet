package com.isapet.service;

import com.isapet.dto.EventoPasarelaRequest;
import com.isapet.dto.PagoResponse;
import com.isapet.exception.FirmaPasarelaInvalidaException;
import org.springframework.stereotype.Service;

/** Recibe las notificaciones de la pasarela: solo aplica las que vienen con una firma válida. */
@Service
public class WebhookPasarelaService {

    private final FirmaPasarela firma;
    private final PagoService pagoService;

    public WebhookPasarelaService(FirmaPasarela firma, PagoService pagoService) {
        this.firma = firma;
        this.pagoService = pagoService;
    }

    public PagoResponse procesar(EventoPasarelaRequest evento, String firmaRecibida) {
        if (!firma.esValida(evento, firmaRecibida)) {
            throw new FirmaPasarelaInvalidaException();
        }
        return pagoService.aplicarResultado(evento);
    }
}
