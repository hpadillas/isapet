package com.isapet.repository;

import com.isapet.entity.EstadoPago;
import com.isapet.entity.Pago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.referencia = :referencia")
    Optional<Pago> findByReferenciaForUpdate(@Param("referencia") String referencia);

    @EntityGraph(attributePaths = {"afiliado", "cuota", "cuota.plan", "bono", "bono.servicio", "bono.mascota"})
    Optional<Pago> findByReferencia(String referencia);

    @EntityGraph(attributePaths = {"afiliado", "cuota", "cuota.plan", "bono", "bono.servicio", "bono.mascota"})
    List<Pago> findAllByAfiliadoIdOrderByCreadoEnDescIdDesc(Long afiliadoId);

    @EntityGraph(attributePaths = {"afiliado", "cuota", "cuota.plan", "bono", "bono.servicio", "bono.mascota"})
    List<Pago> findAllByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByCreadoEnDescIdDesc(
            OffsetDateTime desde, OffsetDateTime hastaExclusiva);

    /** Pago en línea aún sin confirmar de una cuota, para no abrir dos sesiones de pago a la vez. */
    Optional<Pago> findFirstByCuotaIdAndEstado(Long cuotaId, EstadoPago estado);
}
