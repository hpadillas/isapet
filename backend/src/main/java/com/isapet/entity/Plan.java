package com.isapet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plan_afiliacion")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
    @OrderBy("vigenteDesde ASC")
    private List<VersionPlan> versiones = new ArrayList<>();

    protected Plan() {
    }

    public Plan(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public void agregarVersion(VersionPlan version) {
        versiones.add(version);
    }

    public void quitarVersion(VersionPlan version) {
        versiones.remove(version);
    }

    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
    }

    public void desactivar() {
        activo = false;
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

    public boolean isActivo() {
        return activo;
    }

    public List<VersionPlan> getVersiones() {
        return versiones;
    }
}
