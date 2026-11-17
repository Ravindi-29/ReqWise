package com.reqwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "requirement_diffs")
public class RequirementDiff {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_version_id", nullable = false)
    private DocumentVersion baseVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_version_id", nullable = false)
    private DocumentVersion targetVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_req_id")
    private Requirement baseReq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_req_id")
    private Requirement targetReq;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType; // UNCHANGED, MODIFIED_EDITORIAL, MODIFIED_BREAKING, ADDED, DELETED

    @Column(name = "similarity_score", precision = 5, scale = 4)
    private BigDecimal similarityScore;

    @Column(name = "impact_summary", columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "impacted_req_ids", columnDefinition = "TEXT")
    private String impactedReqIds = "[]"; // Stored as JSON array of UUID strings

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RequirementDiff() {
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.impactedReqIds == null) {
            this.impactedReqIds = "[]";
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public DocumentVersion getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(DocumentVersion baseVersion) {
        this.baseVersion = baseVersion;
    }

    public DocumentVersion getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(DocumentVersion targetVersion) {
        this.targetVersion = targetVersion;
    }

    public Requirement getBaseReq() {
        return baseReq;
    }

    public void setBaseReq(Requirement baseReq) {
        this.baseReq = baseReq;
    }

    public Requirement getTargetReq() {
        return targetReq;
    }

    public void setTargetReq(Requirement targetReq) {
        this.targetReq = targetReq;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(BigDecimal similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public void setImpactSummary(String impactSummary) {
        this.impactSummary = impactSummary;
    }

    public String getImpactedReqIds() {
        return impactedReqIds;
    }

    public void setImpactedReqIds(String impactedReqIds) {
        this.impactedReqIds = impactedReqIds;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
