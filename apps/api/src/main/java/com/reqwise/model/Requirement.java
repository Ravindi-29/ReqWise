package com.reqwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requirements")
public class Requirement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @Column(name = "req_code", nullable = false, length = 64)
    private String reqCode; // e.g. "FR-001", "NFR-SEC-004"

    @Column(name = "section_path", length = 255)
    private String sectionPath;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "normalized_text", nullable = false, columnDefinition = "TEXT")
    private String normalizedText;

    @Column(name = "req_type", nullable = false, length = 32)
    private String reqType; // FUNCTIONAL, NON_FUNCTIONAL, CONSTRAINT

    @Column(name = "nfr_category", length = 64)
    private String nfrCategory; // SECURITY, PERFORMANCE, AVAILABILITY...

    @Column(name = "ears_pattern", length = 64)
    private String earsPattern; // EVENT_DRIVEN, UBIQUITOUS...

    @Column(name = "priority", length = 32)
    private String priority; // MUST, SHOULD, COULD, WONT

    @Column(name = "clarity_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal clarityScore;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "bounding_box", columnDefinition = "TEXT")
    private String boundingBox; // Stored as JSON: {"x0": ..., "y0": ..., "x1": ..., "y1": ...}

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding; // Stored as comma-separated floats or JSON array (1536 dims)

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus; // PENDING, APPROVED, NEEDS_REVISION

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "primaryReq", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequirementIssue> issuesPrimary = new ArrayList<>();

    @OneToMany(mappedBy = "relatedReq")
    private List<RequirementIssue> issuesRelated = new ArrayList<>();

    public Requirement() {
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.clarityScore == null) {
            this.clarityScore = new BigDecimal("100.00");
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = "PENDING";
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

    public String getReqCode() {
        return reqCode;
    }

    public void setReqCode(String reqCode) {
        this.reqCode = reqCode;
    }

    public String getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(String sectionPath) {
        this.sectionPath = sectionPath;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public String getReqType() {
        return reqType;
    }

    public void setReqType(String reqType) {
        this.reqType = reqType;
    }

    public String getNfrCategory() {
        return nfrCategory;
    }

    public void setNfrCategory(String nfrCategory) {
        this.nfrCategory = nfrCategory;
    }

    public String getEarsPattern() {
        return earsPattern;
    }

    public void setEarsPattern(String earsPattern) {
        this.earsPattern = earsPattern;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public BigDecimal getClarityScore() {
        return clarityScore;
    }

    public void setClarityScore(BigDecimal clarityScore) {
        this.clarityScore = clarityScore;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(String boundingBox) {
        this.boundingBox = boundingBox;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<RequirementIssue> getIssuesPrimary() {
        return issuesPrimary;
    }

    public void setIssuesPrimary(List<RequirementIssue> issuesPrimary) {
        this.issuesPrimary = issuesPrimary;
    }

    public List<RequirementIssue> getIssuesRelated() {
        return issuesRelated;
    }

    public void setIssuesRelated(List<RequirementIssue> issuesRelated) {
        this.issuesRelated = issuesRelated;
    }
}
