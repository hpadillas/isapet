package com.isapet.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodoAfiliacionTest {

    @Test
    void elPeriodoVaDelDiaDeAfiliacionAlDiaAnteriorDelMesSiguiente() {
        LocalDate inicio = LocalDate.of(2026, 10, 20);

        PeriodoAfiliacion primero = PeriodoAfiliacion.queContiene(inicio, LocalDate.of(2026, 11, 19));
        PeriodoAfiliacion segundo = PeriodoAfiliacion.queContiene(inicio, LocalDate.of(2026, 11, 20));

        assertThat(primero.desde()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(primero.hasta()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(segundo.desde()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(segundo.hasta()).isEqualTo(LocalDate.of(2026, 12, 19));
    }

    @Test
    void lasAfiliacionesDeFinDeMesNoDejanDiasSinPeriodo() {
        LocalDate inicio = LocalDate.of(2026, 1, 31);

        // 31 de enero + 1 mes = 28 de febrero: ese día ya empieza el segundo periodo.
        assertThat(PeriodoAfiliacion.queContiene(inicio, LocalDate.of(2026, 2, 27)).desde()).isEqualTo(inicio);
        PeriodoAfiliacion febrero = PeriodoAfiliacion.queContiene(inicio, LocalDate.of(2026, 2, 28));
        assertThat(febrero.desde()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(febrero.hastaExclusiva()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(PeriodoAfiliacion.queContiene(inicio, LocalDate.of(2026, 3, 30))).isEqualTo(febrero);
    }
}
