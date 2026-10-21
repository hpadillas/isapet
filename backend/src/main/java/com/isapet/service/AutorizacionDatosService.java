package com.isapet.service;

import com.isapet.dto.AutorizacionDatosResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.AutorizacionDatos;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.AutorizacionDatosRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Registro y consulta de la prueba de autorización de tratamiento de datos (Ley 1581 de 2012). */
@Service
public class AutorizacionDatosService {

    static final String MEDIO_REGISTRO = "Formulario de registro de afiliado en ISAPET";
    static final String MEDIO_POSTERIOR = "Registro posterior en la ficha del afiliado";

    private final AutorizacionDatosRepository autorizacionRepository;
    private final AfiliadoRepository afiliadoRepository;
    private final Clock clock;

    public AutorizacionDatosService(
            AutorizacionDatosRepository autorizacionRepository,
            AfiliadoRepository afiliadoRepository,
            Clock clock) {
        this.autorizacionRepository = autorizacionRepository;
        this.afiliadoRepository = afiliadoRepository;
        this.clock = clock;
    }

    /** Guarda la prueba de la autorización con la versión vigente de la política y quién la registró. */
    @Transactional
    public AutorizacionDatos registrar(Afiliado afiliado, String medio) {
        return autorizacionRepository.save(new AutorizacionDatos(
                afiliado, PoliticaPrivacidad.VERSION, OffsetDateTime.now(clock), usuarioActual(), medio));
    }

    @Transactional(readOnly = true)
    public AutorizacionDatosResponse consultar(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return autorizacionRepository.findFirstByAfiliadoIdOrderByOtorgadaEnDesc(afiliadoId)
                .map(AutorizacionDatosService::response)
                .orElse(new AutorizacionDatosResponse(false, null, null, null, null));
    }

    /** Para afiliados registrados antes de este requisito: deja la prueba con la versión vigente. */
    @Transactional
    public AutorizacionDatosResponse registrarPosterior(Long afiliadoId) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        return autorizacionRepository.findFirstByAfiliadoIdOrderByOtorgadaEnDesc(afiliadoId)
                .filter(existente -> existente.getVersionPolitica().equals(PoliticaPrivacidad.VERSION))
                .map(AutorizacionDatosService::response)
                .orElseGet(() -> response(registrar(afiliado, MEDIO_POSTERIOR)));
    }

    private static AutorizacionDatosResponse response(AutorizacionDatos autorizacion) {
        return new AutorizacionDatosResponse(
                true,
                autorizacion.getVersionPolitica(),
                autorizacion.getOtorgadaEn(),
                autorizacion.getRegistradaPor(),
                autorizacion.getMedio());
    }

    private static String usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion == null || autenticacion.getName() == null ? "sistema" : autenticacion.getName();
    }
}
