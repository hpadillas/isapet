package com.isapet.repository;

import com.isapet.entity.AutorizacionDatos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutorizacionDatosRepository extends JpaRepository<AutorizacionDatos, Long> {

    Optional<AutorizacionDatos> findFirstByAfiliadoIdOrderByOtorgadaEnDesc(Long afiliadoId);
}
