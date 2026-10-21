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

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false)
    private Afiliado afiliado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoNotificacion tipo;

    @Column(nullable = false, unique = true, length = 120)
    private String clave;

    @Column(length = 254)
    private String destinatario;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado;

    @Column(length = 500)
    private String error;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    protected Notificacion() {
    }

    public Notificacion(
            Afiliado afiliado,
            TipoNotificacion tipo,
            String clave,
            String destinatario,
            String asunto,
            EstadoNotificacion estado,
            String error,
            OffsetDateTime creadaEn) {
        this.afiliado = afiliado;
        this.tipo = tipo;
        this.clave = clave;
        this.destinatario = destinatario;
        this.asunto = asunto;
        this.estado = estado;
        this.error = error;
        this.creadaEn = creadaEn;
    }

    public Long getId() {
        return id;
    }

    public Afiliado getAfiliado() {
        return afiliado;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getClave() {
        return clave;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAsunto() {
        return asunto;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public String getError() {
        return error;
    }

    public OffsetDateTime getCreadaEn() {
        return creadaEn;
    }
}
