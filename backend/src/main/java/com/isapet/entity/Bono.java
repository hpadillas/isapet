package com.isapet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bono")
public class Bono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false)
    private Afiliado afiliado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private ServicioVeterinario servicio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "valor_base", nullable = false, precision = 14, scale = 0)
    private BigDecimal valorBase;

    @Column(name = "porcentaje_descuento", nullable = false)
    private Integer porcentajeDescuento;

    @Column(name = "valor_pagado", nullable = false, precision = 14, scale = 0)
    private BigDecimal valorPagado;

    @Column(name = "fecha_compra", nullable = false)
    private OffsetDateTime fechaCompra;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBono estado = EstadoBono.DISPONIBLE;

    protected Bono() {
    }

    public Bono(
            Afiliado afiliado,
            Mascota mascota,
            ServicioVeterinario servicio,
            Plan plan,
            TarifaBono tarifa,
            OffsetDateTime fechaCompra) {
        this(afiliado, mascota, servicio, plan, tarifa, fechaCompra, false);
    }

    /** {@code pendienteDePago}: comprado en línea; queda disponible cuando la pasarela confirme el pago. */
    public Bono(
            Afiliado afiliado,
            Mascota mascota,
            ServicioVeterinario servicio,
            Plan plan,
            TarifaBono tarifa,
            OffsetDateTime fechaCompra,
            boolean pendienteDePago) {
        this.estado = pendienteDePago ? EstadoBono.PENDIENTE_PAGO : EstadoBono.DISPONIBLE;
        this.afiliado = afiliado;
        this.mascota = mascota;
        this.servicio = servicio;
        this.plan = plan;
        this.valorBase = tarifa.valorBase();
        this.porcentajeDescuento = tarifa.porcentajeDescuento();
        this.valorPagado = tarifa.valorAPagar();
        this.fechaCompra = fechaCompra;
    }

    public boolean estaDisponible() {
        return estado == EstadoBono.DISPONIBLE;
    }

    public void confirmarPago() {
        if (estado != EstadoBono.PENDIENTE_PAGO) {
            throw new IllegalStateException("El bono no está pendiente de pago.");
        }
        estado = EstadoBono.DISPONIBLE;
    }

    public void anularPorPagoRechazado() {
        if (estado != EstadoBono.PENDIENTE_PAGO) {
            throw new IllegalStateException("El bono no está pendiente de pago.");
        }
        estado = EstadoBono.ANULADO;
    }

    public void marcarUsado() {
        if (!estaDisponible()) {
            throw new IllegalStateException("El bono ya fue utilizado.");
        }
        estado = EstadoBono.USADO;
    }

    public String getCodigo() {
        return id == null ? null : "BN-%06d".formatted(id);
    }

    public Long getId() {
        return id;
    }

    public Afiliado getAfiliado() {
        return afiliado;
    }

    public Mascota getMascota() {
        return mascota;
    }

    public ServicioVeterinario getServicio() {
        return servicio;
    }

    public Plan getPlan() {
        return plan;
    }

    public BigDecimal getValorBase() {
        return valorBase;
    }

    public Integer getPorcentajeDescuento() {
        return porcentajeDescuento;
    }

    public BigDecimal getValorPagado() {
        return valorPagado;
    }

    public OffsetDateTime getFechaCompra() {
        return fechaCompra;
    }

    public EstadoBono getEstado() {
        return estado;
    }
}
