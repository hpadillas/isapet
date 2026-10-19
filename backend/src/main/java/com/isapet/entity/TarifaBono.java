package com.isapet.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor de un servicio después de aplicar el descuento del plan, redondeado a pesos enteros.
 */
public record TarifaBono(BigDecimal valorBase, int porcentajeDescuento, BigDecimal valorAPagar) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static TarifaBono calcular(BigDecimal valorBase, int porcentajeDescuento) {
        if (porcentajeDescuento < 0 || porcentajeDescuento > 100) {
            throw new IllegalArgumentException("El porcentaje de descuento debe estar entre 0 y 100.");
        }
        BigDecimal valorAPagar = valorBase
                .multiply(BigDecimal.valueOf(100L - porcentajeDescuento))
                .divide(CIEN, 0, RoundingMode.HALF_UP);
        return new TarifaBono(valorBase, porcentajeDescuento, valorAPagar);
    }

    public BigDecimal ahorro() {
        return valorBase.subtract(valorAPagar);
    }
}
