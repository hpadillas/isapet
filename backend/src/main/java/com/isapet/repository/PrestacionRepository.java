package com.isapet.repository;

import com.isapet.entity.Prestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
