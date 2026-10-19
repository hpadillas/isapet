package com.isapet.repository;

import com.isapet.entity.Afiliacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AfiliacionRepository extends JpaRepository<Afiliacion, Long> {

    @EntityGraph(attributePaths = {"plan", "mascotasCubiertas"})
    Optional<Afiliacion> findByAfiliadoId(Long afiliadoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Afiliacion a where a.afiliado.id = :afiliadoId")
    Optional<Afiliacion> findByAfiliadoIdForUpdate(@Param("afiliadoId") Long afiliadoId);
}
