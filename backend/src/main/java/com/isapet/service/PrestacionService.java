package com.isapet.service;

import com.isapet.dto.PrestacionResponse;
import com.isapet.dto.RegistrarPrestacionRequest;
import com.isapet.entity.Bono;
import com.isapet.entity.Mascota;
import com.isapet.entity.Prestacion;
import com.isapet.entity.ServicioVeterinario;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.BonoNotFoundException;
import com.isapet.exception.BusinessRuleException;
import com.isapet.exception.MascotaNotFoundException;
import com.isapet.exception.ServicioNotFoundException;
import com.isapet.mapper.BonoPrestacionMapper;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.BonoRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.PrestacionRepository;
import com.isapet.repository.ServicioVeterinarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PrestacionService {

    private final AfiliadoRepository afiliadoRepository;
    private final MascotaRepository mascotaRepository;
    private final ServicioVeterinarioRepository servicioRepository;
    private final BonoRepository bonoRepository;
    private final PrestacionRepository prestacionRepository;
    private final BonoPrestacionMapper mapper;
    private final Clock clock;

    public PrestacionService(
            AfiliadoRepository afiliadoRepository,
            MascotaRepository mascotaRepository,
            ServicioVeterinarioRepository servicioRepository,
            BonoRepository bonoRepository,
            PrestacionRepository prestacionRepository,
            BonoPrestacionMapper mapper,
            Clock clock) {
        this.afiliadoRepository = afiliadoRepository;
        this.mascotaRepository = mascotaRepository;
        this.servicioRepository = servicioRepository;
        this.bonoRepository = bonoRepository;
        this.prestacionRepository = prestacionRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PrestacionResponse> listar(Long afiliadoId, Long mascotaId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        if (mascotaId != null) {
            mascotaDelAfiliado(afiliadoId, mascotaId);
        }
        return prestacionRepository.buscarPorAfiliado(afiliadoId, mascotaId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public PrestacionResponse registrar(Long afiliadoId, RegistrarPrestacionRequest request) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        Mascota mascota = mascotaDelAfiliado(afiliadoId, request.mascotaId());
        ServicioVeterinario servicio = servicioRepository.findById(request.servicioId())
                .orElseThrow(() -> new ServicioNotFoundException(request.servicioId()));

        Bono bono = null;
        if (request.bonoId() != null) {
            bono = bonoRepository.findByIdForUpdate(request.bonoId())
                    .orElseThrow(() -> new BonoNotFoundException(request.bonoId()));
            validarBono(bono, afiliadoId, mascota, servicio, request);
            bono.marcarUsado();
        }

        String observaciones = request.observaciones() == null || request.observaciones().isBlank()
                ? null
                : request.observaciones().trim();
        Prestacion prestacion = prestacionRepository.saveAndFlush(new Prestacion(
                mascota,
                servicio,
                bono,
                request.fecha(),
                request.prestador().trim(),
                observaciones,
                OffsetDateTime.now(clock)));
        return mapper.toResponse(prestacion);
    }

    private void validarBono(
            Bono bono,
            Long afiliadoId,
            Mascota mascota,
            ServicioVeterinario servicio,
            RegistrarPrestacionRequest request) {
        if (!bono.getAfiliado().getId().equals(afiliadoId)) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " no pertenece al afiliado.");
        }
        if (!bono.estaDisponible()) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " ya fue utilizado.");
        }
        if (!bono.getMascota().getId().equals(mascota.getId())) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " fue comprado para otra mascota.");
        }
        if (!bono.getServicio().getId().equals(servicio.getId())) {
            throw new BusinessRuleException("El bono " + bono.getCodigo() + " corresponde a otro servicio.");
        }
        if (request.fecha().isBefore(bono.getFechaCompra().atZoneSameInstant(clock.getZone()).toLocalDate())) {
            throw new BusinessRuleException("La prestación no puede ser anterior a la compra del bono.");
        }
    }

    private Mascota mascotaDelAfiliado(Long afiliadoId, Long mascotaId) {
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        if (!mascota.getAfiliado().getId().equals(afiliadoId)) {
            throw new BusinessRuleException("La mascota con id " + mascotaId + " no pertenece al afiliado.");
        }
        return mascota;
    }
}
