package com.isapet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "afiliacion")
public class Afiliacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false, unique = true)
    private Afiliado afiliado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "afiliacion_mascota",
            joinColumns = @JoinColumn(name = "afiliacion_id"),
            inverseJoinColumns = @JoinColumn(name = "mascota_id"))
    private Set<Mascota> mascotasCubiertas = new LinkedHashSet<>();

    protected Afiliacion() {
    }

    public Afiliacion(Afiliado afiliado, Plan plan, LocalDate fechaInicio, Collection<Mascota> mascotasCubiertas) {
        this.afiliado = afiliado;
        this.plan = plan;
        this.fechaInicio = fechaInicio;
        this.mascotasCubiertas = new LinkedHashSet<>(mascotasCubiertas);
    }

    public void cambiarPlan(Plan nuevoPlan, LocalDate fechaInicio, Collection<Mascota> mascotas) {
        if (!plan.getId().equals(nuevoPlan.getId())) {
            this.plan = nuevoPlan;
            this.fechaInicio = fechaInicio;
        }
        this.mascotasCubiertas.clear();
        this.mascotasCubiertas.addAll(mascotas);
    }

    public boolean cubre(Mascota mascota) {
        return mascotasCubiertas.stream().anyMatch(cubierta -> cubierta.getId().equals(mascota.getId()));
    }

    public Long getId() {
        return id;
    }

    public Afiliado getAfiliado() {
        return afiliado;
    }

    public Plan getPlan() {
        return plan;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public Set<Mascota> getMascotasCubiertas() {
        return Set.copyOf(mascotasCubiertas);
    }
}
