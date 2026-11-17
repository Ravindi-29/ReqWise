package com.reqwise.repository;

import com.reqwise.model.RequirementIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequirementIssueRepository extends JpaRepository<RequirementIssue, UUID> {
    List<RequirementIssue> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    List<RequirementIssue> findByPrimaryReqId(UUID primaryReqId);
    long countByVersionIdAndSeverity(UUID versionId, String severity);
    long countByVersionIdAndIssueCategory(UUID versionId, String issueCategory);

    @Query("SELECT i FROM RequirementIssue i WHERE i.version.id = :versionId " +
           "AND (:severity IS NULL OR i.severity = :severity) " +
           "AND (:category IS NULL OR i.issueCategory = :category) " +
           "AND (:isResolved IS NULL OR i.isResolved = :isResolved) " +
           "ORDER BY i.createdAt DESC")
    List<RequirementIssue> filterIssues(
            @Param("versionId") UUID versionId,
            @Param("severity") String severity,
            @Param("category") String category,
            @Param("isResolved") Boolean isResolved
    );
}
