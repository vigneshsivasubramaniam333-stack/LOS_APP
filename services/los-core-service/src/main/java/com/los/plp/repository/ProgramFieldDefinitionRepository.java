package com.los.plp.repository;

import com.los.plp.model.entity.ProgramFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramFieldDefinitionRepository extends JpaRepository<ProgramFieldDefinition, UUID> {

    Optional<ProgramFieldDefinition> findByFieldKey(String fieldKey);

    boolean existsByFieldKey(String fieldKey);

    List<ProgramFieldDefinition> findAllByOrderBySortOrderAscLabelAsc();

    List<ProgramFieldDefinition> findByActiveTrueOrderBySortOrderAscLabelAsc();
}
