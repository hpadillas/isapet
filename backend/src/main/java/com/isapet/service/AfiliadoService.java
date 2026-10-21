package com.isapet.service;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.dto.ActualizarAfiliadoRequest;
import com.isapet.dto.AfiliadoDetalleResponse;
import com.isapet.dto.AfiliadoListaResponse;
import com.isapet.dto.EstadoPlanAfiliado;
import com.isapet.entity.CuotaPlan;
import com.isapet.entity.Afiliado;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.exception.DuplicateNumeroIdentificacionException;
import com.isapet.mapper.AfiliadoMapper;
import com.isapet.repository.AfiliadoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfiliadoService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliadoMapper afiliadoMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;
    private final AutorizacionDatosService autorizacionService;

    public AfiliadoService(
            AfiliadoRepository afiliadoRepository,
            AfiliadoMapper afiliadoMapper,
            Clock clock,
            ApplicationEventPublisher eventos,
            AutorizacionDatosService autorizacionService) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliadoMapper = afiliadoMapper;
        this.clock = clock;
        this.eventos = eventos;
        this.autorizacionService = autorizacionService;
    }

    @Transactional(readOnly = true)
    public Page<AfiliadoListaResponse> listar(String busqueda, EstadoPlanAfiliado estado, Pageable pageable) {
        String criterio = busqueda == null ? "" : busqueda.trim();
        // Una cuota pendiente que empezó antes de esta fecha ya venció su plazo de pago (plan suspendido).
        LocalDate limite = LocalDate.now(clock).minusDays(CuotaPlan.DIAS_PLAZO_PAGO);
        return afiliadoRepository.buscar(criterio, estado == null ? "" : estado.name(), limite, pageable);
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
        // Prueba de la autorización de tratamiento de datos (Ley 1581 de 2012, arts. 9 y 17-b).
        autorizacionService.registrar(guardado, AutorizacionDatosService.MEDIO_REGISTRO);
        eventos.publishEvent(new EventoAfiliado.AfiliadoRegistrado(guardado.getId()));

        return afiliadoMapper.toCrearResponse(guardado);
    }
}