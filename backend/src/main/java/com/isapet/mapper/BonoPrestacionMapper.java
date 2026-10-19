package com.isapet.mapper;

import com.isapet.dto.BonoResponse;
import com.isapet.dto.PrestacionResponse;
import com.isapet.entity.Bono;
import com.isapet.entity.Prestacion;
import org.springframework.stereotype.Component;

@Component
public class BonoPrestacionMapper {

    public BonoResponse toResponse(Bono bono) {
        return new BonoResponse(
                bono.getId(),
                bono.getCodigo(),
                bono.getMascota().getId(),
                bono.getMascota().getNombre(),
                bono.getServicio().getId(),
                bono.getServicio().getNombre(),
                bono.getServicio().getTipo().name(),
                bono.getPlan().getCodigo(),
                bono.getValorBase(),
                bono.getPorcentajeDescuento(),
                bono.getValorPagado(),
                bono.getFechaCompra(),
                bono.getEstado().name());
    }

    public PrestacionResponse toResponse(Prestacion prestacion) {
        Bono bono = prestacion.getBono();
        return new PrestacionResponse(
                prestacion.getId(),
                prestacion.getFecha(),
                prestacion.getMascota().getId(),
                prestacion.getMascota().getNombre(),
                prestacion.getServicio().getId(),
                prestacion.getServicio().getNombre(),
                prestacion.getServicio().getTipo().name(),
                prestacion.getPrestador(),
                prestacion.getObservaciones(),
                bono == null ? null : bono.getId(),
                bono == null ? null : bono.getCodigo(),
                bono == null ? null : bono.getValorPagado());
    }
}
