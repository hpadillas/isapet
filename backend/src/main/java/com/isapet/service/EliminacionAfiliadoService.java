package com.isapet.service;

import com.isapet.dto.ResumenEliminacionAfiliadoResponse;
import com.isapet.entity.Afiliado;
import com.isapet.entity.Mascota;
import com.isapet.exception.AfiliadoNotFoundException;
import com.isapet.repository.AfiliacionRepository;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.BonoRepository;
import com.isapet.repository.CuotaPlanRepository;
import com.isapet.repository.MascotaRepository;
import com.isapet.repository.NotificacionRepository;
import com.isapet.repository.PrestacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elimina un afiliado con todo lo que depende de él: mascotas y su historia clínica (prestaciones), bonos,
 * afiliación, cuotas y notificaciones. No se puede deshacer.
 */
@Service
public class EliminacionAfiliadoService {

    private final AfiliadoRepository afiliadoRepository;
    private final AfiliacionRepository afiliacionRepository;
    private final MascotaRepository mascotaRepository;
    private final PrestacionRepository prestacionRepository;
    private final BonoRepository bonoRepository;
    private final CuotaPlanRepository cuotaRepository;
    private final NotificacionRepository notificacionRepository;

    public EliminacionAfiliadoService(
            AfiliadoRepository afiliadoRepository,
            AfiliacionRepository afiliacionRepository,
            MascotaRepository mascotaRepository,
            PrestacionRepository prestacionRepository,
            BonoRepository bonoRepository,
            CuotaPlanRepository cuotaRepository,
            NotificacionRepository notificacionRepository) {
        this.afiliadoRepository = afiliadoRepository;
        this.afiliacionRepository = afiliacionRepository;
        this.mascotaRepository = mascotaRepository;
        this.prestacionRepository = prestacionRepository;
        this.bonoRepository = bonoRepository;
        this.cuotaRepository = cuotaRepository;
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional(readOnly = true)
    public ResumenEliminacionAfiliadoResponse resumen(Long afiliadoId) {
        Afiliado afiliado = afiliadoRepository.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        return new ResumenEliminacionAfiliadoResponse(
                afiliado.getId(),
                afiliado.getNumeroIdentificacion(),
                afiliado.getNombres() + " " + afiliado.getApellidos(),
                afiliacionRepository.findByAfiliadoId(afiliadoId).map(afiliacion -> afiliacion.getPlan().getNombre()).orElse(null),
                mascotaRepository.findAllByAfiliadoIdOrderByIdAsc(afiliadoId).stream().map(Mascota::getNombre).toList(),
                prestacionRepository.contarDelAfiliado(afiliadoId),
                bonoRepository.countByAfiliadoId(afiliadoId),
                cuotaRepository.countByAfiliadoId(afiliadoId));
    }

    @Transactional
    public void eliminar(Long afiliadoId) {
        if (!afiliadoRepository.existsById(afiliadoId)) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        // Orden de las llaves foráneas: primero lo que referencia a mascotas y bonos, al final el afiliado.
        prestacionRepository.eliminarDelAfiliado(afiliadoId);
        bonoRepository.eliminarDelAfiliado(afiliadoId);
        cuotaRepository.eliminarDelAfiliado(afiliadoId);
        afiliacionRepository.eliminarDelAfiliado(afiliadoId);
        notificacionRepository.eliminarDelAfiliado(afiliadoId);
        mascotaRepository.eliminarDelAfiliado(afiliadoId);
        afiliadoRepository.deleteById(afiliadoId);
    }
}
