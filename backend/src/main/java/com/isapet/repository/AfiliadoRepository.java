package com.isapet.repository;

import com.isapet.entity.Afiliado;
import com.isapet.dto.AfiliadoListaResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AfiliadoRepository extends JpaRepository<Afiliado, Long> {

	boolean existsByNumeroIdentificacion(String numeroIdentificacion);

	List<Afiliado> findTop5ByOrderByIdDesc();

	/**
	 * Listado con búsqueda por texto y filtro por estado del plan (SIN_PLAN, POR_ACTIVAR, ACTIVO, SUSPENDIDO
	 * o '' para todos). {@code limite}: una cuota pendiente con inicio anterior a esa fecha ya venció su plazo.
	 */
	@Query(value = """
			select new com.isapet.dto.AfiliadoListaResponse(
				a.id, a.numeroIdentificacion, a.nombres, a.apellidos,
				a.celular, a.correo, a.direccion,
				(select count(m) from Mascota m where m.afiliado.id = a.id),
				(select p.nombre from Afiliacion f join f.plan p where f.afiliado.id = a.id),
				case
					when not exists (select 1 from Afiliacion f where f.afiliado.id = a.id) then 'SIN_PLAN'
					when exists (select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = true) then 'POR_ACTIVAR'
					when exists (select 1 from CuotaPlan c where c.afiliado.id = a.id
						and c.estado = com.isapet.entity.EstadoCuota.PENDIENTE and c.deActivacion = false
						and c.periodoDesde < :limite) then 'SUSPENDIDO'
					else 'ACTIVO'
				end)
			from Afiliado a
			where (:busqueda = ''
				or lower(a.numeroIdentificacion) like lower(concat('%', :busqueda, '%'))
				or lower(a.nombres) like lower(concat('%', :busqueda, '%'))
				or lower(a.apellidos) like lower(concat('%', :busqueda, '%'))
				or lower(a.correo) like lower(concat('%', :busqueda, '%')))
			and (:estado = ''
				or (:estado = 'SIN_PLAN' and not exists (select 1 from Afiliacion f where f.afiliado.id = a.id))
				or (:estado = 'POR_ACTIVAR' and exists (
					select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = true))
				or (:estado = 'SUSPENDIDO'
					and exists (select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = false)
					and exists (select 1 from CuotaPlan c where c.afiliado.id = a.id
						and c.estado = com.isapet.entity.EstadoCuota.PENDIENTE and c.deActivacion = false and c.periodoDesde < :limite))
				or (:estado = 'ACTIVO'
					and exists (select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = false)
					and not exists (select 1 from CuotaPlan c where c.afiliado.id = a.id
						and c.estado = com.isapet.entity.EstadoCuota.PENDIENTE and c.deActivacion = false and c.periodoDesde < :limite)))
			""",
			countQuery = """
			select count(a) from Afiliado a
			where (:busqueda = ''
				or lower(a.numeroIdentificacion) like lower(concat('%', :busqueda, '%'))
				or lower(a.nombres) like lower(concat('%', :busqueda, '%'))
				or lower(a.apellidos) like lower(concat('%', :busqueda, '%'))
				or lower(a.correo) like lower(concat('%', :busqueda, '%')))
			and (:estado = ''
				or (:estado = 'SIN_PLAN' and not exists (select 1 from Afiliacion f where f.afiliado.id = a.id))
				or (:estado = 'POR_ACTIVAR' and exists (
					select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = true))
				or (:estado = 'SUSPENDIDO'
					and exists (select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = false)
					and exists (select 1 from CuotaPlan c where c.afiliado.id = a.id
						and c.estado = com.isapet.entity.EstadoCuota.PENDIENTE and c.deActivacion = false and c.periodoDesde < :limite))
				or (:estado = 'ACTIVO'
					and exists (select 1 from Afiliacion f where f.afiliado.id = a.id and f.pendienteActivacion = false)
					and not exists (select 1 from CuotaPlan c where c.afiliado.id = a.id
						and c.estado = com.isapet.entity.EstadoCuota.PENDIENTE and c.deActivacion = false and c.periodoDesde < :limite)))
			""")
	Page<AfiliadoListaResponse> buscar(
			@Param("busqueda") String busqueda,
			@Param("estado") String estado,
			@Param("limite") LocalDate limite,
			Pageable pageable);
}