package com.isapet.repository;

import com.isapet.entity.BeneficioPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BeneficioPlanRepository extends JpaRepository<BeneficioPlan, Long> {

    @EntityGraph(attributePaths = "servicio")
    List<BeneficioPlan> findAllByPlanId(Long planId);

    @EntityGraph(attributePaths = "servicio")
    List<BeneficioPlan> findAllByPlanIdIn(Collection<Long> planIds);

    Optional<BeneficioPlan> findByPlanIdAndServicioId(Long planId, Long servicioId);
}
