package com.isapet.repository;

import com.isapet.entity.Bono;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BonoRepository extends JpaRepository<Bono, Long> {

    @EntityGraph(attributePaths = {"mascota", "servicio", "plan"})
    List<Bono> findAllByAfiliadoIdOrderByFechaCompraDesc(Long afiliadoId);

    @EntityGraph(attributePaths = {"mascota", "servicio", "plan"})
    List<Bono> findAllByAfiliadoIdAndFechaCompraGreaterThanEqualAndFechaCompraLessThanOrderByFechaCompraDesc(
            Long afiliadoId,
            OffsetDateTime desde,
            OffsetDateTime hastaExclusiva);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bono b where b.id = :id")
    Optional<Bono> findByIdForUpdate(@Param("id") Long id);

    long countByAfiliadoId(Long afiliadoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Bono b where b.afiliado.id = :afiliadoId")
    void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);
}
