package com.isapet.mapper;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.entity.Afiliado;
import org.springframework.stereotype.Component;

@Component
public class AfiliadoMapper {

    public Afiliado toEntity(CrearAfiliadoRequest request) {
        if (request == null) return null;
        return new Afiliado(
                request.numeroIdentificacion(),
                request.nombres(),
                request.apellidos(),
                request.celular(),
                request.correo(),
                request.direccion()
        );
    }

    public CrearAfiliadoResponse toCrearResponse(Afiliado afiliado) {
        if (afiliado == null) return null;
        return new CrearAfiliadoResponse(
                afiliado.getId(),
                afiliado.getNumeroIdentificacion(),
                afiliado.getNombres(),
                afiliado.getApellidos(),
                afiliado.getCelular(),
                afiliado.getCorreo(),
                afiliado.getDireccion()
        );
    }

    public AfiliadoDetalleResponse toDetalleResponse(Afiliado afiliado) {
        if (afiliado == null) return null;
        return new AfiliadoDetalleResponse(
                afiliado.getId(),
                afiliado.getNumeroIdentificacion(),
                afiliado.getNombres(),
                afiliado.getApellidos(),
                afiliado.getCelular(),
                afiliado.getCorreo(),
                afiliado.getDireccion()
        );
    }
}
