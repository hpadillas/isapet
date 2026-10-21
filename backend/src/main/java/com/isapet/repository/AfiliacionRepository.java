package com.isapet.repository;

import com.isapet.entity.Afiliacion;
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

public interface AfiliacionRepository extends JpaRepository<Afiliacion, Long> {

    @EntityGraph(attributePaths = {"plan", "mascotasCubiertas"})
    Optional<Afiliacion> findByAfiliadoId(Long afiliadoId);

    @EntityGraph(attributePaths = {"afiliado", "plan"})
    List<Afiliacion> findAllBy();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Afiliacion a where a.fechaFin < :fecha")
    List<Afiliacion> findConCancelacionCumplidaForUpdate(@Param("fecha") LocalDate fecha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Afiliacion a where a.fechaCambioProgramado <= :fecha")
    List<Afiliacion> findConCambioProgramadoHastaForUpdate(@Param("fecha") LocalDate fecha);

    @Query("""
            select count(a) from Afiliacion a
            where a.planProgramado.id = :planId
              and a.fechaCambioProgramado >= :desde and a.fechaCambioProgramado < :hasta
            """)
    long contarCambiosProgramadosAlPlanEntre(
            @Param("planId") Long planId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hastaExclusiva);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Afiliacion a where a.afiliado.id = :afiliadoId")
    void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Afiliacion a where a.afiliado.id = :afiliadoId")
    Optional<Afiliacion> findByAfiliadoIdForUpdate(@Param("afiliadoId") Long afiliadoId);
}
