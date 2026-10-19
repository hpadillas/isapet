package com.isapet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "servicio_veterinario")
public class ServicioVeterinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoServicio tipo;

    @Column(name = "valor_base", nullable = false, precision = 14, scale = 0)
    private BigDecimal valorBase;

    @Column(nullable = false)
    private boolean activo = true;

    protected ServicioVeterinario() {
    }

    public ServicioVeterinario(String codigo, String nombre, TipoServicio tipo, BigDecimal valorBase) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorBase = valorBase;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoServicio getTipo() {
        return tipo;
    }

    public BigDecimal getValorBase() {
        return valorBase;
    }

    public boolean isActivo() {
        return activo;
    }
}
