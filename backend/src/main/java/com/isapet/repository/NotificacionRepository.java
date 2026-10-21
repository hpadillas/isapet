package com.isapet.repository;

import com.isapet.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    boolean existsByClave(String clave);

    List<Notificacion> findAllByAfiliadoIdOrderByCreadaEnDescIdDesc(Long afiliadoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notificacion n where n.afiliado.id = :afiliadoId")
    void eliminarDelAfiliado(@Param("afiliadoId") Long afiliadoId);
}
