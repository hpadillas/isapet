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

import java.time.OffsetDateTime;

/**
 * Prueba de la autorización del titular para el tratamiento de sus datos personales
 * (Ley 1581 de 2012, arts. 9 y 17 literal b).
 */
@Entity
@Table(name = "autorizacion_datos")
public class AutorizacionDatos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false)
    private Afiliado afiliado;

    @Column(name = "version_politica", nullable = false, length = 20)
    private String versionPolitica;

    @Column(name = "otorgada_en", nullable = false)
    private OffsetDateTime otorgadaEn;

    @Column(name = "registrada_por", nullable = false, length = 100)
    private String registradaPor;

    @Column(nullable = false, length = 150)
    private String medio;

    protected AutorizacionDatos() {
    }

    public AutorizacionDatos(Afiliado afiliado, String versionPolitica, OffsetDateTime otorgadaEn, String registradaPor, String medio) {
        this.afiliado = afiliado;
        this.versionPolitica = versionPolitica;
        this.otorgadaEn = otorgadaEn;
        this.registradaPor = registradaPor;
        this.medio = medio;
    }

    public Long getId() {
        return id;
    }

    public Afiliado getAfiliado() {
        return afiliado;
    }

    public String getVersionPolitica() {
        return versionPolitica;
    }

    public OffsetDateTime getOtorgadaEn() {
        return otorgadaEn;
    }

    public String getRegistradaPor() {
        return registradaPor;
    }

    public String getMedio() {
        return medio;
    }
}
