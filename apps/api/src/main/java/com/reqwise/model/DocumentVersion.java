package com.reqwise.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "document_versions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_document_version", columnNames = {"document_id", "version_number"})
    }
)
public class DocumentVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "version_label", nullable = false, length = 64)
    private String versionLabel;

    @Column(name = "source_file_path", nullable = false, columnDefinition = "TEXT")
    private String sourceFilePath;

    @Column(name = "preview_pdf_path", columnDefinition = "TEXT")
    private String previewPdfPath;

    @Column(name = "file_type", nullable = false, length = 16)
    private String fileType; // PDF, DOCX

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus; // QUEUED, PARSING, EXTRACTING, ANALYZING_QUALITY, DETECTING_CONFLICTS, BUILDING_RTM, COMPLETED, FAILED

    @Column(name = "overall_quality_score", precision = 5, scale = 2)
    private BigDecimal overallQualityScore;

    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary; // Stored as JSON string

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("reqCode ASC")
    private List<Requirement> requirements = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequirementIssue> issues = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TraceabilityLink> traceabilityLinks = new ArrayList<>();

    public DocumentVersion() {
    }

    public DocumentVersion(Document document, Integer versionNumber, String versionLabel, String sourceFilePath, String fileType) {
        this.id = UUID.randomUUID();
        this.document = document;
        this.versionNumber = versionNumber;
        this.versionLabel = versionLabel;
        this.sourceFilePath = sourceFilePath;
        this.fileType = fileType;
        this.processingStatus = "QUEUED";
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.processingStatus == null) {
            this.processingStatus = "QUEUED";
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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    public String getPreviewPdfPath() {
        return previewPdfPath;
    }

    public void setPreviewPdfPath(String previewPdfPath) {
        this.previewPdfPath = previewPdfPath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public BigDecimal getOverallQualityScore() {
        return overallQualityScore;
    }

    public void setOverallQualityScore(BigDecimal overallQualityScore) {
        this.overallQualityScore = overallQualityScore;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<Requirement> requirements) {
        this.requirements = requirements;
    }

    public List<RequirementIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<RequirementIssue> issues) {
        this.issues = issues;
    }

    public List<TraceabilityLink> getTraceabilityLinks() {
        return traceabilityLinks;
    }

    public void setTraceabilityLinks(List<TraceabilityLink> traceabilityLinks) {
        this.traceabilityLinks = traceabilityLinks;
    }
}
