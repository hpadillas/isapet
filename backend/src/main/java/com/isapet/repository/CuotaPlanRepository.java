package com.isapet.repository;

import com.isapet.entity.CuotaPlan;
import com.isapet.entity.EstadoCuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CuotaPlanRepository extends JpaRepository<CuotaPlan, Long> {

    boolean existsByAfiliadoIdAndPeriodoDesdeAndEstadoNot(Long afiliadoId, LocalDate periodoDesde, EstadoCuota estado);

    /** Cuotas del afiliado en un estado, la más antigua primero. */
    List<CuotaPlan> findAllByAfiliadoIdAndEstadoOrderByPeriodoDesdeAsc(Long afiliadoId, EstadoCuota estado);

    @EntityGraph(attributePaths = {"afiliado", "plan"})
    List<CuotaPlan> findAllByEstadoAndPeriodoDesdeLessThan(EstadoCuota estado, LocalDate fecha);

    /** Cuotas que pueden vencer (excluye la de activación, que no suspende). */
    @EntityGraph(attributePaths = {"afiliado", "plan"})
    List<CuotaPlan> findAllByEstadoAndDeActivacionFalseAndPeriodoDesdeLessThan(EstadoCuota estado, LocalDate fecha);

    boolean existsByAfiliadoIdAndEstadoAndDeActivacionFalseAndPeriodoDesdeLessThan(
            Long afiliadoId, EstadoCuota estado, LocalDate fecha);

    /**
     * El plan está suspendido en {@code fecha} si alguna cuota sigue pendiente después de su plazo de pago
     * (fecha de pago + {@value CuotaPlan#DIAS_PLAZO_PAGO} días). Se reactiva sola al pagarla.
     */
    default boolean planSuspendido(Long afiliadoId, LocalDate fecha) {
        return existsByAfiliadoIdAndEstadoAndDeActivacionFalseAndPeriodoDesdeLessThan(
                afiliadoId, EstadoCuota.PENDIENTE, fecha.minusDays(CuotaPlan.DIAS_PLAZO_PAGO));
    }

    @EntityGraph(attributePaths = "plan")
    List<CuotaPlan> findAllByAfiliadoIdOrderByPeriodoDesdeDesc(Long afiliadoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuotaPlan c where c.id = :id and c.afiliado.id = :afiliadoId")
    Optional<CuotaPlan> findByIdAndAfiliadoIdForUpdate(@Param("id") Long id, @Param("afiliadoId") Long afiliadoId);

    long countByAfiliadoId(Long afiliadoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CuotaPlan c where c.afiliado.id = :afiliadoId")
    void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);
}
