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
import java.util.Locale;
import java.util.UUID;

/**
 * Transacción del libro de pagos: el pago de una cuota del plan o de un bono, con su medio, canal y estado.
 * Nunca guarda datos de tarjeta: en línea, la tarjeta solo la ve la pasarela (PCI DSS).
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "afiliado_id", nullable = false)
    private Afiliado afiliado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConceptoPago concepto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id")
    private CuotaPlan cuota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bono_id")
    private Bono bono;

    @Column(nullable = false, precision = 14)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", length = 20)
    private MedioPago medioPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalPago canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "confirmado_en")
    private OffsetDateTime confirmadoEn;

    @Column(name = "transaccion_pasarela", length = 80)
    private String transaccionPasarela;

    @Column(length = 300)
    private String detalle;

    protected Pago() {
    }

    private Pago(Afiliado afiliado, ConceptoPago concepto, CuotaPlan cuota, Bono bono, BigDecimal monto,
                 CanalPago canal, OffsetDateTime creadoEn) {
        this.referencia = "PG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        this.afiliado = afiliado;
        this.concepto = concepto;
        this.cuota = cuota;
        this.bono = bono;
        this.monto = monto;
        this.canal = canal;
        this.estado = EstadoPago.PENDIENTE;
        this.creadoEn = creadoEn;
    }

    public static Pago deCuota(CuotaPlan cuota, CanalPago canal, OffsetDateTime creadoEn) {
        return new Pago(cuota.getAfiliado(), ConceptoPago.CUOTA_PLAN, cuota, null, cuota.getValor(), canal, creadoEn);
    }

    public static Pago deBono(Bono bono, CanalPago canal, OffsetDateTime creadoEn) {
        return new Pago(bono.getAfiliado(), ConceptoPago.BONO, null, bono, bono.getValorPagado(), canal, creadoEn);
    }

    public boolean estaPendiente() {
        return estado == EstadoPago.PENDIENTE;
    }

    public void aprobar(LocalDate fechaPago, MedioPago medioPago, String transaccionPasarela, OffsetDateTime confirmadoEn) {
        exigirPendiente();
        this.estado = EstadoPago.APROBADO;
        this.fechaPago = fechaPago;
        this.medioPago = medioPago;
        this.transaccionPasarela = transaccionPasarela;
        this.confirmadoEn = confirmadoEn;
    }

    public void rechazar(MedioPago medioPago, String transaccionPasarela, String motivo, OffsetDateTime confirmadoEn) {
        exigirPendiente();
        this.estado = EstadoPago.RECHAZADO;
        this.medioPago = medioPago;
        this.transaccionPasarela = transaccionPasarela;
        this.detalle = motivo;
        this.confirmadoEn = confirmadoEn;
    }

    /** Nota para conciliación, p. ej. un pago aprobado de una cuota que ya estaba saldada. */
    public void anotar(String nota) {
        this.detalle = nota;
    }

    private void exigirPendiente() {
        if (!estaPendiente()) {
            throw new IllegalStateException("El pago " + referencia + " ya fue procesado.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getReferencia() {
        return referencia;
    }

    public Afiliado getAfiliado() {
        return afiliado;
    }

    public ConceptoPago getConcepto() {
        return concepto;
    }

    public CuotaPlan getCuota() {
        return cuota;
    }

    public Bono getBono() {
        return bono;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public MedioPago getMedioPago() {
        return medioPago;
    }

    public CanalPago getCanal() {
        return canal;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getConfirmadoEn() {
        return confirmadoEn;
    }

    public String getTransaccionPasarela() {
        return transaccionPasarela;
    }

    public String getDetalle() {
        return detalle;
    }
}
