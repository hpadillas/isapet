package com.isapet.repository;

import com.isapet.entity.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PrestacionRepository extends JpaRepository<Prestacion, Long> {

    @Query("""
            select p from Prestacion p
            join fetch p.mascota m
            join fetch p.servicio
            left join fetch p.bono
            where m.afiliado.id = :afiliadoId
              and (:mascotaId is null or m.id = :mascotaId)
            order by p.fecha desc, p.id desc
            """)
    List<Prestacion> buscarPorAfiliado(@Param("afiliadoId") Long afiliadoId, @Param("mascotaId") Long mascotaId);

    @Query("""
            select p from Prestacion p
            join fetch p.mascota m
            join fetch p.servicio
            where m.afiliado.id = :afiliadoId
              and p.cobertura = com.isapet.entity.CoberturaPrestacion.PLAN
              and p.fecha >= :desde and p.fecha < :hasta
            order by p.fecha desc, p.id desc
            """)
    List<Prestacion> buscarIncluidasEnPlan(
            @Param("afiliadoId") Long afiliadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hastaExclusiva);

    @Query("""
            select count(p) from Prestacion p
            where p.mascota.afiliado.id = :afiliadoId
              and p.cobertura = com.isapet.entity.CoberturaPrestacion.PLAN
              and p.fecha >= :desde and p.fecha < :hasta
            """)
    long contarIncluidasEnPlan(
            @Param("afiliadoId") Long afiliadoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hastaExclusiva);

    long countByFechaGreaterThanEqualAndFechaLessThan(LocalDate desde, LocalDate hastaExclusiva);

    @Query("select count(p) from Prestacion p where p.mascota.afiliado.id = :afiliadoId")
    long contarDelAfiliado(@Param("afiliadoId") Long afiliadoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Prestacion p where p.mascota.id in (select m.id from Mascota m where m.afiliado.id = :afiliadoId)")
    void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);
}
