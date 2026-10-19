package com.isapet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "beneficio_plan")
public class BeneficioPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private ServicioVeterinario servicio;

    @Column(name = "porcentaje_descuento", nullable = false)
    private Integer porcentajeDescuento;

    protected BeneficioPlan() {
    }

    public BeneficioPlan(Plan plan, ServicioVeterinario servicio, Integer porcentajeDescuento) {
        this.plan = plan;
        this.servicio = servicio;
        this.porcentajeDescuento = porcentajeDescuento;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public ServicioVeterinario getServicio() {
        return servicio;
    }

    public Integer getPorcentajeDescuento() {
        return porcentajeDescuento;
    }
}
