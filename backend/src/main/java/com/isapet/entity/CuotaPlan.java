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
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Mensualidad del plan correspondiente a un periodo de afiliación. */
@Entity
@Table(name = "cuota_plan")
public class CuotaPlan {

    /** Días que tiene el afiliado para pagar después de la fecha de pago (el inicio del periodo). */
    public static final int DIAS_PLAZO_PAGO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false)
    private Afiliado afiliado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(nullable = false, precision = 14)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuota estado = EstadoCuota.PENDIENTE;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", length = 20)
    private MedioPago medioPago;

    @Column(name = "pago_registrado_en")
    private OffsetDateTime pagoRegistradoEn;

    @Column(name = "anulada_en")
    private OffsetDateTime anuladaEn;

    /** Primera cuota de una afiliación: pagarla activa el plan. No vence ni suspende. */
    @Column(name = "de_activacion", nullable = false)
    private boolean deActivacion;

    protected CuotaPlan() {
    }

    public CuotaPlan(Afiliado afiliado, Plan plan, LocalDate periodoDesde, LocalDate periodoHasta, BigDecimal valor) {
        this.afiliado = afiliado;
        this.plan = plan;
        this.periodoDesde = periodoDesde;
        this.periodoHasta = periodoHasta;
        this.valor = valor;
    }

    public boolean estaPagada() {
        return estado == EstadoCuota.PAGADA;
    }

    public boolean estaPendiente() {
        return estado == EstadoCuota.PENDIENTE;
    }

    /** Último día para pagar sin que el plan se suspenda; la cuota de activación no tiene (null). */
    public LocalDate fechaLimitePago() {
        return deActivacion ? null : periodoDesde.plusDays(DIAS_PLAZO_PAGO);
    }

    /** Pendiente y con el plazo de pago ya vencido en {@code fecha}. La de activación nunca vence. */
    public boolean estaVencida(LocalDate fecha) {
        return estaPendiente() && !deActivacion && fecha.isAfter(fechaLimitePago());
    }

    public void marcarDeActivacion() {
        this.deActivacion = true;
    }

    public boolean isDeActivacion() {
        return deActivacion;
    }

    /** Al activar el plan, la cuota de activación pasa a cubrir el primer periodo real. */
    public void reprogramarPeriodo(LocalDate desde, LocalDate hasta) {
        if (!deActivacion || !estaPendiente()) {
            throw new IllegalStateException("Solo se reprograma la cuota de activación pendiente.");
        }
        this.periodoDesde = desde;
        this.periodoHasta = hasta;
    }

    public void anular(OffsetDateTime anuladaEn) {
        if (!estaPendiente()) {
            throw new IllegalStateException("Solo se anula una cuota pendiente.");
        }
        this.estado = EstadoCuota.ANULADA;
        this.anuladaEn = anuladaEn;
    }

    public void registrarPago(LocalDate fechaPago, MedioPago medioPago, OffsetDateTime registradoEn) {
        this.estado = EstadoCuota.PAGADA;
        this.fechaPago = fechaPago;
        this.medioPago = medioPago;
        this.pagoRegistradoEn = registradoEn;
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

    public LocalDate getPeriodoDesde() {
        return periodoDesde;
    }

    public LocalDate getPeriodoHasta() {
        return periodoHasta;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public EstadoCuota getEstado() {
        return estado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public MedioPago getMedioPago() {
        return medioPago;
    }
}
