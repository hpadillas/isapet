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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "prestacion")
public class Prestacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mascota_id", nullable = false)
    private Mascota mascota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private ServicioVeterinario servicio;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bono_id", unique = true)
    private Bono bono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoberturaPrestacion cobertura;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String prestador;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "registrada_en", nullable = false)
    private OffsetDateTime registradaEn;

    protected Prestacion() {
    }

    public Prestacion(
            Mascota mascota,
            ServicioVeterinario servicio,
            Bono bono,
            CoberturaPrestacion cobertura,
            LocalDate fecha,
            String prestador,
            String observaciones,
            OffsetDateTime registradaEn) {
        this.mascota = mascota;
        this.servicio = servicio;
        this.bono = bono;
        this.cobertura = cobertura;
        this.fecha = fecha;
        this.prestador = prestador;
        this.observaciones = observaciones;
        this.registradaEn = registradaEn;
    }

    public Long getId() {
        return id;
    }

    public Mascota getMascota() {
        return mascota;
    }

    public ServicioVeterinario getServicio() {
        return servicio;
    }

    public Bono getBono() {
        return bono;
    }

    public CoberturaPrestacion getCobertura() {
        return cobertura;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getPrestador() {
        return prestador;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public OffsetDateTime getRegistradaEn() {
        return registradaEn;
    }
}
