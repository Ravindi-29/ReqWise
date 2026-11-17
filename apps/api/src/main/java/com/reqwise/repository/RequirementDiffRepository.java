package com.reqwise.repository;

import com.reqwise.model.RequirementDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequirementDiffRepository extends JpaRepository<RequirementDiff, UUID> {
    List<RequirementDiff> findByBaseVersionIdAndTargetVersionId(UUID baseVersionId, UUID targetVersionId);
}
