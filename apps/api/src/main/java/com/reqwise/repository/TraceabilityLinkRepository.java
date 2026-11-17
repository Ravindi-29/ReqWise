package com.reqwise.repository;

import com.reqwise.model.TraceabilityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TraceabilityLinkRepository extends JpaRepository<TraceabilityLink, UUID> {
    List<TraceabilityLink> findByVersionId(UUID versionId);
    List<TraceabilityLink> findBySourceNodeId(UUID sourceNodeId);
    List<TraceabilityLink> findByTargetNodeId(UUID targetNodeId);
}
