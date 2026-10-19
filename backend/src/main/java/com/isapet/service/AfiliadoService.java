package com.isapet.service;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.ActualizarAfiliadoRequest;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.entity.Afiliado;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.DuplicateNumeroIdentificacionException;
import com.isapet.mapper.AfiliadoMapper;
import com.isapet.repository.AfiliadoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfiliadoService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliadoMapper afiliadoMapper;

    public AfiliadoService(AfiliadoRepository afiliadoRepository, AfiliadoMapper afiliadoMapper) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliadoMapper = afiliadoMapper;
    }

    @Transactional(readOnly = true)
    public Page<AfiliadoListaResponse> listar(String busqueda, Pageable pageable) {
        String criterio = busqueda == null ? "" : busqueda.trim();
        return afiliadoRepository.buscar(criterio, pageable);
    }

    @Transactional(readOnly = true)
    public AfiliadoDetalleResponse obtener(Long afiliadoId) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        return afiliadoMapper.toDetalleResponse(afiliado);
    }

    @Transactional
    public AfiliadoDetalleResponse actualizar(Long afiliadoId, ActualizarAfiliadoRequest request) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        afiliado.actualizarDatosContacto(
                request.nombres(), request.apellidos(), request.celular(), request.correo(), request.direccion());
        return afiliadoMapper.toDetalleResponse(afiliado);
    }

    @Transactional
    public CrearAfiliadoResponse crear(CrearAfiliadoRequest request) {
        if (afiliadoRepository.existsByNumeroIdentificacion(request.numeroIdentificacion())) {
            throw new DuplicateNumeroIdentificacionException(request.numeroIdentificacion());
        }

        Afiliado afiliado = afiliadoMapper.toEntity(request);
        Afiliado guardado = afiliadoRepository.save(afiliado);

        return afiliadoMapper.toCrearResponse(guardado);
    }
}