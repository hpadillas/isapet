package com.isapet.service;

import com.isapet.dto.CrearMascotaRequest;
import com.isapet.dto.CrearMascotaResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.MascotaNotFoundException;
import com.isapet.mapper.MascotaMapper;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MascotaService {

    private final AfiliadoRepository afiliadoRepository;
    private final MascotaRepository mascotaRepository;
    private final MascotaMapper mascotaMapper;

    public MascotaService(AfiliadoRepository afiliadoRepository, MascotaRepository mascotaRepository, MascotaMapper mascotaMapper) {
        this.afiliadoRepository = afiliadoRepository;
        this.mascotaRepository = mascotaRepository;
        this.mascotaMapper = mascotaMapper;
    }

    @Transactional(readOnly = true)
    public List<CrearMascotaResponse> listar(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return mascotaRepository.findAllByAfiliadoIdOrderByIdAsc(afiliadoId).stream()
                .map(mascotaMapper::toResponse)
                .toList();
    }

    @Transactional
    public CrearMascotaResponse actualizar(Long mascotaId, CrearMascotaRequest request) {
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        mascota.actualizarDatos(request.nombre(), request.especie(), request.raza(), request.edad(), request.color());
        return mascotaMapper.toResponse(mascota);
    }

    @Transactional
    public void eliminar(Long mascotaId) {
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        mascotaRepository.delete(mascota);
    }

    @Transactional
    public CrearMascotaResponse crear(Long afiliadoId, CrearMascotaRequest request) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));

        Mascota mascota = mascotaMapper.toEntity(request, afiliado);
        Mascota guardada = mascotaRepository.save(mascota);

        return mascotaMapper.toResponse(guardada);
    }
}