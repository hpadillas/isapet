package com.isapet.repository;

import com.isapet.entity.Mascota;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

	List<Mascota> findAllByAfiliadoIdOrderByIdAsc(Long afiliadoId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Mascota m where m.afiliado.id = :afiliadoId")
	void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);
}