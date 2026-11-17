package com.reqwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "traceability_links",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_traceability_link", columnNames = {"source_node_id", "target_node_id", "link_type"})
    }
)
public class TraceabilityLink {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    private Requirement sourceNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_node_id", nullable = false)
    private Requirement targetNode;

    @Column(name = "link_type", nullable = false, length = 32)
    private String linkType; // REFINES, DEPENDS_ON, CONSTRAINS, VERIFIED_BY

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "is_human_verified", nullable = false)
    private Boolean isHumanVerified = false;

    public TraceabilityLink() {
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.isHumanVerified == null) {
            this.isHumanVerified = false;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DocumentVersion getVersion() {
        return version;
    }

    public void setVersion(DocumentVersion version) {
        this.version = version;
    }

    public Requirement getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(Requirement sourceNode) {
        this.sourceNode = sourceNode;
    }

    public Requirement getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(Requirement targetNode) {
        this.targetNode = targetNode;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Boolean getIsHumanVerified() {
        return isHumanVerified;
    }

    public void setIsHumanVerified(Boolean isHumanVerified) {
        this.isHumanVerified = isHumanVerified;
    }
}
