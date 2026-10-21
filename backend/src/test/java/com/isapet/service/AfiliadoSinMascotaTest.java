package com.isapet.service;

import com.isapet.dto.CrearAfiliadoRequest;
import com.isapet.dto.CrearAfiliadoResponse;
import com.isapet.repository.AfiliadoRepository;
import com.isapet.repository.MascotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AfiliadoSinMascotaTest {

    @Autowired
    private AfiliadoService afiliadoService;

    @Autowired
    private AfiliadoRepository afiliadoRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Test
    @Transactional
    void registraAfiliadoSinCrearMascotasAutomaticamente() {
        String identificacion = "F01-NP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        CrearAfiliadoResponse response = afiliadoService.crear(new CrearAfiliadoRequest(
                identificacion, "Ana", "Gomez", "3001234567", "ana@example.com", "Calle 1", true));

        assertThat(afiliadoRepository.findById(response.id())).isPresent();
        assertThat(mascotaRepository.findAll())
                .noneMatch(mascota -> mascota.getAfiliado().getId().equals(response.id()));
    }
}