package com.reqwise.repository;

import com.reqwise.model.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
    List<Requirement> findByVersionIdOrderByReqCodeAsc(UUID versionId);
    Optional<Requirement> findByVersionIdAndReqCode(UUID versionId, String reqCode);

    long countByVersionId(UUID versionId);
    long countByVersionIdAndReqType(UUID versionId, String reqType);

    @Query("SELECT r FROM Requirement r WHERE r.version.id = :versionId " +
           "AND (:reqType IS NULL OR r.reqType = :reqType) " +
           "AND (:reviewStatus IS NULL OR r.reviewStatus = :reviewStatus) " +
           "ORDER BY r.reqCode ASC")
    List<Requirement> filterRequirements(
            @Param("versionId") UUID versionId,
            @Param("reqType") String reqType,
            @Param("reviewStatus") String reviewStatus
    );
}
