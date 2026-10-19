package com.isapet.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TarifaBonoTest {

    @Test
    void aplicaElDescuentoYRedondeaAPesosEnteros() {
        TarifaBono tarifa = TarifaBono.calcular(new BigDecimal("45555"), 35);

        assertThat(tarifa.valorAPagar()).isEqualByComparingTo("29611");
        assertThat(tarifa.ahorro()).isEqualByComparingTo("15944");
    }

    @Test
    void sinDescuentoSePagaElValorBase() {
        TarifaBono tarifa = TarifaBono.calcular(new BigDecimal("60000"), 0);

        assertThat(tarifa.valorAPagar()).isEqualByComparingTo("60000");
    }

    @Test
    void rechazaPorcentajesFueraDeRango() {
        assertThrows(IllegalArgumentException.class, () -> TarifaBono.calcular(BigDecimal.TEN, 101));
        assertThrows(IllegalArgumentException.class, () -> TarifaBono.calcular(BigDecimal.TEN, -1));
    }
}
