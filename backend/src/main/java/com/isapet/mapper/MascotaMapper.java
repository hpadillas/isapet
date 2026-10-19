package com.isapet.mapper;

import com.isapet.dto.CrearMascotaRequest;
import com.isapet.dto.CrearMascotaResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import org.springframework.stereotype.Component;

@Component
public class MascotaMapper {

    public Mascota toEntity(CrearMascotaRequest request, Afiliado afiliado) {
        if (request == null || afiliado == null) return null;
        return new Mascota(
                request.nombre(),
                request.especie(),
                request.raza(),
                request.edad(),
                request.color(),
                afiliado
        );
    }

    public CrearMascotaResponse toResponse(Mascota mascota) {
        if (mascota == null) return null;
        return new CrearMascotaResponse(
                mascota.getId(),
                mascota.getAfiliado().getId(),
                mascota.getNombre(),
                mascota.getEspecie(),
                mascota.getRaza(),
                mascota.getEdad(),
                mascota.getColor()
        );
    }
}
