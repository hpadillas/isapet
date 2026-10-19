import os
import shutil

BASE_DIR = "/Users/ferney/.gemini/antigravity-ide/scratch/isapet/backend/src/main/java/com/isapet"
OLD_DIRS = ["controller", "dto", "entity", "exception", "repository", "service", "validation"]

# Target structure
NEW_DIRS = [
    "domain/model",
    "domain/port/in",
    "domain/port/out",
    "application/service",
    "infrastructure/persistence/entity",
    "infrastructure/persistence/repository",
    "infrastructure/persistence/adapter",
    "infrastructure/persistence/mapper",
    "infrastructure/web/controller",
    "infrastructure/web/dto",
    "infrastructure/web/exception",
    "infrastructure/config"
]

def create_dirs():
    for d in NEW_DIRS:
        os.makedirs(os.path.join(BASE_DIR, d), exist_ok=True)

# 1. Domain Models
afiliado_domain = """package com.isapet.domain.model;

public class Afiliado {
    private Long id;
    private String numeroIdentificacion;
    private String nombres;
    private String apellidos;
    private String celular;
    private String correo;
    private String direccion;

    public Afiliado(Long id, String numeroIdentificacion, String nombres, String apellidos, String celular, String correo, String direccion) {
        this.id = id;
        this.numeroIdentificacion = numeroIdentificacion;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.celular = celular;
        this.correo = correo;
        this.direccion = direccion;
    }

    // Getters
    public Long getId() { return id; }
    public String getNumeroIdentificacion() { return numeroIdentificacion; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getCelular() { return celular; }
    public String getCorreo() { return correo; }
    public String getDireccion() { return direccion; }
    
    public void actualizarDatosContacto(String nombres, String apellidos, String celular, String correo, String direccion) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.celular = celular;
        this.correo = correo;
        this.direccion = direccion;
    }
}
"""

mascota_domain = """package com.isapet.domain.model;

public class Mascota {
    private Long id;
    private String nombre;
    private String especie;
    private String raza;
    private Integer edad;
    private String color;
    private Long afiliadoId;

    public Mascota(Long id, String nombre, String especie, String raza, Integer edad, String color, Long afiliadoId) {
        this.id = id;
        this.nombre = nombre;
        this.especie = especie;
        this.raza = raza;
        this.edad = edad;
        this.color = color;
        this.afiliadoId = afiliadoId;
    }

    public void actualizarInfo(String nombre, String especie, String raza, Integer edad, String color) {
        this.nombre = nombre;
        this.especie = especie;
        this.raza = raza;
        this.edad = edad;
        this.color = color;
    }

    // Getters
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEspecie() { return especie; }
    public String getRaza() { return raza; }
    public Integer getEdad() { return edad; }
    public String getColor() { return color; }
    public Long getAfiliadoId() { return afiliadoId; }
}
"""

# 2. Domain Ports (Out)
afiliado_port_out = """package com.isapet.domain.port.out;

import com.isapet.domain.model.Afiliado;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.isapet.infrastructure.web.dto.AfiliadoListaResponse;

public interface AfiliadoRepositoryPort {
    Afiliado save(Afiliado afiliado);
    Optional<Afiliado> findById(Long id);
    boolean existsByNumeroIdentificacion(String numeroIdentificacion);
    Page<AfiliadoListaResponse> buscar(String criterio, Pageable pageable);
}
"""

mascota_port_out = """package com.isapet.domain.port.out;

import com.isapet.domain.model.Mascota;
import java.util.List;
import java.util.Optional;

public interface MascotaRepositoryPort {
    Mascota save(Mascota mascota);
    Optional<Mascota> findById(Long id);
    List<Mascota> findByAfiliadoId(Long afiliadoId);
    void deleteById(Long id);
}
"""

# 3. Application Services
afiliado_service = """package com.isapet.application.service;

import com.isapet.domain.model.Afiliado;
import com.isapet.domain.port.out.AfiliadoRepositoryPort;
import com.isapet.infrastructure.web.dto.*;
import com.isapet.infrastructure.web.exception.AfiliadoNotFoundException;
import com.isapet.infrastructure.web.exception.DuplicateNumeroIdentificacionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AfiliadoService {

    private final AfiliadoRepositoryPort afiliadoRepositoryPort;

    public AfiliadoService(AfiliadoRepositoryPort afiliadoRepositoryPort) {
        this.afiliadoRepositoryPort = afiliadoRepositoryPort;
    }

    public Page<AfiliadoListaResponse> listar(String busqueda, Pageable pageable) {
        String criterio = busqueda == null ? "" : busqueda.trim();
        return afiliadoRepositoryPort.buscar(criterio, pageable);
    }

    public AfiliadoDetalleResponse obtener(Long afiliadoId) {
        Afiliado afiliado = afiliadoRepositoryPort.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        return detalle(afiliado);
    }

    public AfiliadoDetalleResponse actualizar(Long afiliadoId, ActualizarAfiliadoRequest request) {
        Afiliado afiliado = afiliadoRepositoryPort.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));
        
        afiliado.actualizarDatosContacto(
                request.nombres(), request.apellidos(), request.celular(), request.correo(), request.direccion());
        
        Afiliado guardado = afiliadoRepositoryPort.save(afiliado);
        return detalle(guardado);
    }

    public CrearAfiliadoResponse crear(CrearAfiliadoRequest request) {
        if (afiliadoRepositoryPort.existsByNumeroIdentificacion(request.numeroIdentificacion())) {
            throw new DuplicateNumeroIdentificacionException(request.numeroIdentificacion());
        }

        Afiliado afiliado = new Afiliado(
                null,
                request.numeroIdentificacion(),
                request.nombres(),
                request.apellidos(),
                request.celular(),
                request.correo(),
                request.direccion());

        Afiliado guardado = afiliadoRepositoryPort.save(afiliado);

        return new CrearAfiliadoResponse(
            guardado.getId(),
            guardado.getNumeroIdentificacion(),
            guardado.getNombres(),
            guardado.getApellidos(),
            guardado.getCelular(),
            guardado.getCorreo(),
            guardado.getDireccion());
    }

    private AfiliadoDetalleResponse detalle(Afiliado afiliado) {
        return new AfiliadoDetalleResponse(
                afiliado.getId(), afiliado.getNumeroIdentificacion(), afiliado.getNombres(),
                afiliado.getApellidos(), afiliado.getCelular(), afiliado.getCorreo(), afiliado.getDireccion());
    }
}
"""

mascota_service = """package com.isapet.application.service;

import com.isapet.domain.model.Afiliado;
import com.isapet.domain.model.Mascota;
import com.isapet.domain.port.out.AfiliadoRepositoryPort;
import com.isapet.domain.port.out.MascotaRepositoryPort;
import com.isapet.infrastructure.web.dto.CrearMascotaRequest;
import com.isapet.infrastructure.web.dto.CrearMascotaResponse;
import com.isapet.infrastructure.web.exception.AfiliadoNotFoundException;
import com.isapet.infrastructure.web.exception.MascotaNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MascotaService {

    private final MascotaRepositoryPort mascotaRepositoryPort;
    private final AfiliadoRepositoryPort afiliadoRepositoryPort;

    public MascotaService(MascotaRepositoryPort mascotaRepositoryPort, AfiliadoRepositoryPort afiliadoRepositoryPort) {
        this.mascotaRepositoryPort = mascotaRepositoryPort;
        this.afiliadoRepositoryPort = afiliadoRepositoryPort;
    }

    public CrearMascotaResponse crear(Long afiliadoId, CrearMascotaRequest request) {
        Afiliado afiliado = afiliadoRepositoryPort.findById(afiliadoId)
                .orElseThrow(() -> new AfiliadoNotFoundException(afiliadoId));

        Mascota mascota = new Mascota(
                null,
                request.nombre(),
                request.especie(),
                request.raza(),
                request.edad(),
                request.color(),
                afiliado.getId()
        );

        Mascota guardada = mascotaRepositoryPort.save(mascota);
        return mapToResponse(guardada);
    }

    public List<CrearMascotaResponse> listar(Long afiliadoId) {
        if (afiliadoRepositoryPort.findById(afiliadoId).isEmpty()) {
            throw new AfiliadoNotFoundException(afiliadoId);
        }
        return mascotaRepositoryPort.findByAfiliadoId(afiliadoId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CrearMascotaResponse actualizar(Long mascotaId, CrearMascotaRequest request) {
        Mascota mascota = mascotaRepositoryPort.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));

        mascota.actualizarInfo(
                request.nombre(), request.especie(), request.raza(), request.edad(), request.color());

        Mascota guardada = mascotaRepositoryPort.save(mascota);
        return mapToResponse(guardada);
    }

    public void eliminar(Long mascotaId) {
        mascotaRepositoryPort.findById(mascotaId)
                .orElseThrow(() -> new MascotaNotFoundException(mascotaId));
        mascotaRepositoryPort.deleteById(mascotaId);
    }

    private CrearMascotaResponse mapToResponse(Mascota mascota) {
        return new CrearMascotaResponse(
                mascota.getId(), mascota.getNombre(), mascota.getEspecie(),
                mascota.getRaza(), mascota.getEdad(), mascota.getColor());
    }
}
"""

# 4. Infrastructure JPA Entities & Mappers
afiliado_jpa = """package com.isapet.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "afiliado")
public class AfiliadoJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String numeroIdentificacion;
    private String nombres;
    private String apellidos;
    private String celular;
    private String correo;
    private String direccion;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumeroIdentificacion() { return numeroIdentificacion; }
    public void setNumeroIdentificacion(String n) { this.numeroIdentificacion = n; }
    public String getNombres() { return nombres; }
    public void setNombres(String n) { this.nombres = n; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String a) { this.apellidos = a; }
    public String getCelular() { return celular; }
    public void setCelular(String c) { this.celular = c; }
    public String getCorreo() { return correo; }
    public void setCorreo(String c) { this.correo = c; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String d) { this.direccion = d; }
}
"""

mascota_jpa = """package com.isapet.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "mascota")
public class MascotaJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String especie;
    private String raza;
    private Integer edad;
    private String color;
    
    @Column(name = "afiliado_id")
    private Long afiliadoId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = n; }
    public String getEspecie() { return especie; }
    public void setEspecie(String e) { this.especie = e; }
    public String getRaza() { return raza; }
    public void setRaza(String r) { this.raza = r; }
    public Integer getEdad() { return edad; }
    public void setEdad(Integer e) { this.edad = e; }
    public String getColor() { return color; }
    public void setColor(String c) { this.color = c; }
    public Long getAfiliadoId() { return afiliadoId; }
    public void setAfiliadoId(Long a) { this.afiliadoId = a; }
}
"""

# Mapper
mapper_java = """package com.isapet.infrastructure.persistence.mapper;

import com.isapet.domain.model.Afiliado;
import com.isapet.domain.model.Mascota;
import com.isapet.infrastructure.persistence.entity.AfiliadoJpaEntity;
import com.isapet.infrastructure.persistence.entity.MascotaJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class IsapetMapper {

    public Afiliado toDomain(AfiliadoJpaEntity entity) {
        if (entity == null) return null;
        return new Afiliado(entity.getId(), entity.getNumeroIdentificacion(), entity.getNombres(),
                entity.getApellidos(), entity.getCelular(), entity.getCorreo(), entity.getDireccion());
    }

    public AfiliadoJpaEntity toEntity(Afiliado domain) {
        if (domain == null) return null;
        AfiliadoJpaEntity entity = new AfiliadoJpaEntity();
        entity.setId(domain.getId());
        entity.setNumeroIdentificacion(domain.getNumeroIdentificacion());
        entity.setNombres(domain.getNombres());
        entity.setApellidos(domain.getApellidos());
        entity.setCelular(domain.getCelular());
        entity.setCorreo(domain.getCorreo());
        entity.setDireccion(domain.getDireccion());
        return entity;
    }

    public Mascota toDomain(MascotaJpaEntity entity) {
        if (entity == null) return null;
        return new Mascota(entity.getId(), entity.getNombre(), entity.getEspecie(),
                entity.getRaza(), entity.getEdad(), entity.getColor(), entity.getAfiliadoId());
    }

    public MascotaJpaEntity toEntity(Mascota domain) {
        if (domain == null) return null;
        MascotaJpaEntity entity = new MascotaJpaEntity();
        entity.setId(domain.getId());
        entity.setNombre(domain.getNombre());
        entity.setEspecie(domain.getEspecie());
        entity.setRaza(domain.getRaza());
        entity.setEdad(domain.getEdad());
        entity.setColor(domain.getColor());
        entity.setAfiliadoId(domain.getAfiliadoId());
        return entity;
    }
}
"""

# Adapters
afiliado_adapter = """package com.isapet.infrastructure.persistence.adapter;

import com.isapet.domain.model.Afiliado;
import com.isapet.domain.port.out.AfiliadoRepositoryPort;
import com.isapet.infrastructure.persistence.entity.AfiliadoJpaEntity;
import com.isapet.infrastructure.persistence.mapper.IsapetMapper;
import com.isapet.infrastructure.persistence.repository.SpringDataAfiliadoRepository;
import com.isapet.infrastructure.web.dto.AfiliadoListaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AfiliadoRepositoryAdapter implements AfiliadoRepositoryPort {

    private final SpringDataAfiliadoRepository repository;
    private final IsapetMapper mapper;

    public AfiliadoRepositoryAdapter(SpringDataAfiliadoRepository repository, IsapetMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Afiliado save(Afiliado afiliado) {
        AfiliadoJpaEntity entity = mapper.toEntity(afiliado);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Afiliado> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNumeroIdentificacion(String numeroIdentificacion) {
        return repository.existsByNumeroIdentificacion(numeroIdentificacion);
    }

    @Override
    public Page<AfiliadoListaResponse> buscar(String criterio, Pageable pageable) {
        return repository.buscar(criterio, pageable);
    }
}
"""

mascota_adapter = """package com.isapet.infrastructure.persistence.adapter;

import com.isapet.domain.model.Mascota;
import com.isapet.domain.port.out.MascotaRepositoryPort;
import com.isapet.infrastructure.persistence.entity.MascotaJpaEntity;
import com.isapet.infrastructure.persistence.mapper.IsapetMapper;
import com.isapet.infrastructure.persistence.repository.SpringDataMascotaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MascotaRepositoryAdapter implements MascotaRepositoryPort {

    private final SpringDataMascotaRepository repository;
    private final IsapetMapper mapper;

    public MascotaRepositoryAdapter(SpringDataMascotaRepository repository, IsapetMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mascota save(Mascota mascota) {
        MascotaJpaEntity entity = mapper.toEntity(mascota);
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Mascota> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Mascota> findByAfiliadoId(Long afiliadoId) {
        return repository.findByAfiliadoId(afiliadoId).stream()
                .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
"""

def write_file(path, content):
    with open(os.path.join(BASE_DIR, path), 'w') as f:
        f.write(content)

if __name__ == "__main__":
    create_dirs()
    write_file("domain/model/Afiliado.java", afiliado_domain)
    write_file("domain/model/Mascota.java", mascota_domain)
    write_file("domain/port/out/AfiliadoRepositoryPort.java", afiliado_port_out)
    write_file("domain/port/out/MascotaRepositoryPort.java", mascota_port_out)
    write_file("application/service/AfiliadoService.java", afiliado_service)
    write_file("application/service/MascotaService.java", mascota_service)
    write_file("infrastructure/persistence/entity/AfiliadoJpaEntity.java", afiliado_jpa)
    write_file("infrastructure/persistence/entity/MascotaJpaEntity.java", mascota_jpa)
    write_file("infrastructure/persistence/mapper/IsapetMapper.java", mapper_java)
    write_file("infrastructure/persistence/adapter/AfiliadoRepositoryAdapter.java", afiliado_adapter)
    write_file("infrastructure/persistence/adapter/MascotaRepositoryAdapter.java", mascota_adapter)
    
    # Move DTOs, Controllers, Exceptions to web package using shell commands later
    print("Files created.")
