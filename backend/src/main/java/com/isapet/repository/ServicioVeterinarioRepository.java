package com.isapet.repository;

import com.isapet.entity.ServicioVeterinario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicioVeterinarioRepository extends JpaRepository<ServicioVeterinario, Long> {

    List<ServicioVeterinario> findAllByActivoTrueOrderByTipoAscValorBaseAsc();
}
