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

    /** Plan que reemplazará al actual en {@link #fechaCambioProgramado} (inicio del siguiente periodo). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_programado_id")
    private Plan planProgramado;

    @Column(name = "fecha_cambio_programado")
    private LocalDate fechaCambioProgramado;

    /** Si el afiliado canceló, último día en que conserva el plan; al día siguiente la afiliación termina. */
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /** Asignado pero aún sin pagar la primera cuota: el plan no da beneficios hasta activarse. */
    @Column(name = "pendiente_activacion", nullable = false)
    private boolean pendienteActivacion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "afiliacion_mascota_programada",
            joinColumns = @JoinColumn(name = "afiliacion_id"),
            inverseJoinColumns = @JoinColumn(name = "mascota_id"))
    private Set<Mascota> mascotasProgramadas = new LinkedHashSet<>();

    protected Afiliacion() {
    }

    public Afiliacion(Afiliado afiliado, Plan plan, LocalDate fechaInicio, Collection<Mascota> mascotasCubiertas) {
        this.afiliado = afiliado;
        this.plan = plan;
        this.fechaInicio = fechaInicio;
        this.mascotasCubiertas = new LinkedHashSet<>(mascotasCubiertas);
    }

    /** Cambia las mascotas cubiertas del plan actual; aplica de inmediato. */
    public void actualizarMascotas(Collection<Mascota> mascotas) {
        this.mascotasCubiertas.clear();
        this.mascotasCubiertas.addAll(mascotas);
    }

    /** Deja programado el cambio a otro plan; reemplaza cualquier cambio programado anterior. */
    public void programarCambio(Plan nuevoPlan, LocalDate fechaCambio, Collection<Mascota> mascotas) {
        this.planProgramado = nuevoPlan;
        this.fechaCambioProgramado = fechaCambio;
        this.mascotasProgramadas.clear();
        this.mascotasProgramadas.addAll(mascotas);
    }

    public void cancelarCambioProgramado() {
        this.planProgramado = null;
        this.fechaCambioProgramado = null;
        this.mascotasProgramadas.clear();
    }

    /** Recién asignado: se activará cuando se pague la primera cuota. */
    public void marcarPendienteDeActivacion() {
        this.pendienteActivacion = true;
    }

    /** Activa el plan al pagar la primera cuota: el primer periodo empieza en {@code fecha}. */
    public void activar(LocalDate fecha) {
        if (!pendienteActivacion) {
            throw new IllegalStateException("La afiliación ya está activa.");
        }
        this.pendienteActivacion = false;
        this.fechaInicio = fecha;
    }

    public boolean isPendienteActivacion() {
        return pendienteActivacion;
    }

    /** Programa el fin del plan; también descarta un cambio de plan programado, que ya no aplicaría. */
    public void programarCancelacion(LocalDate ultimoDia) {
        this.fechaFin = ultimoDia;
        cancelarCambioProgramado();
    }

    public void deshacerCancelacion() {
        this.fechaFin = null;
    }

    public boolean tieneCancelacionProgramada() {
        return fechaFin != null;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public boolean tieneCambioProgramado() {
        return planProgramado != null;
    }

    /** Activa el plan programado: su primer periodo empieza en la fecha del cambio. */
    public void aplicarCambioProgramado() {
        if (!tieneCambioProgramado()) {
            throw new IllegalStateException("La afiliación no tiene un cambio de plan programado.");
        }
        this.plan = planProgramado;
        this.fechaInicio = fechaCambioProgramado;
        actualizarMascotas(mascotasProgramadas);
        cancelarCambioProgramado();
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

    public Plan getPlanProgramado() {
        return planProgramado;
    }

    public LocalDate getFechaCambioProgramado() {
        return fechaCambioProgramado;
    }

    public Set<Mascota> getMascotasProgramadas() {
        return Set.copyOf(mascotasProgramadas);
    }
}
