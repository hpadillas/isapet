package com.isapet.service;

import com.isapet.dto.EventoPasarelaRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Firma HMAC-SHA256 de las notificaciones de la pasarela con un secreto compartido. Así se comprueba
 * que la notificación la envió la pasarela y que nadie cambió la referencia, el estado ni el monto.
 */
@Component
public class FirmaPasarela {

    private final byte[] secreto;

    public FirmaPasarela(@Value("${app.pasarela.secreto:secreto-de-pruebas-isapet}") String secreto) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    public String firmar(EventoPasarelaRequest evento) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(cadena(evento).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256.", exception);
        }
    }

    /** Comparación en tiempo constante para no filtrar información sobre la firma esperada. */
    public boolean esValida(EventoPasarelaRequest evento, String firma) {
        if (firma == null || firma.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                firmar(evento).getBytes(StandardCharsets.UTF_8),
                firma.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static String cadena(EventoPasarelaRequest evento) {
        return String.join("|",
                evento.referencia(),
                evento.estado(),
                evento.transaccion(),
                evento.monto().stripTrailingZeros().toPlainString(),
                evento.medioPago().name());
    }
}
