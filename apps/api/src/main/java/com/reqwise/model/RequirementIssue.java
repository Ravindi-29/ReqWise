package com.reqwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "requirement_issues")
public class RequirementIssue {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_req_id", nullable = false)
    private Requirement primaryReq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_req_id")
    private Requirement relatedReq;

    @Column(name = "issue_category", nullable = false, length = 32)
    private String issueCategory; // AMBIGUOUS, INCOMPLETE, MISSING_METRIC, DUPLICATE, CONFLICT, MISSING_REQUIREMENT

    @Column(name = "severity", nullable = false, length = 16)
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW

    @Column(name = "flagged_phrase", columnDefinition = "TEXT")
    private String flaggedPhrase;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "suggested_rewrite", columnDefinition = "TEXT")
    private String suggestedRewrite;

    @Column(name = "similarity_score", precision = 5, scale = 4)
    private BigDecimal similarityScore;

    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RequirementIssue() {
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.isResolved == null) {
            this.isResolved = false;
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

    public DocumentVersion getVersion() {
        return version;
    }

    public void setVersion(DocumentVersion version) {
        this.version = version;
    }

    public Requirement getPrimaryReq() {
        return primaryReq;
    }

    public void setPrimaryReq(Requirement primaryReq) {
        this.primaryReq = primaryReq;
    }

    public Requirement getRelatedReq() {
        return relatedReq;
    }

    public void setRelatedReq(Requirement relatedReq) {
        this.relatedReq = relatedReq;
    }

    public String getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(String issueCategory) {
        this.issueCategory = issueCategory;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getFlaggedPhrase() {
        return flaggedPhrase;
    }

    public void setFlaggedPhrase(String flaggedPhrase) {
        this.flaggedPhrase = flaggedPhrase;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSuggestedRewrite() {
        return suggestedRewrite;
    }

    public void setSuggestedRewrite(String suggestedRewrite) {
        this.suggestedRewrite = suggestedRewrite;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(BigDecimal similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Boolean getIsResolved() {
        return isResolved;
    }

    public void setIsResolved(Boolean isResolved) {
        this.isResolved = isResolved;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
