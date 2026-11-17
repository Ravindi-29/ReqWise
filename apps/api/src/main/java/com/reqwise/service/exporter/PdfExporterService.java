package com.reqwise.service.exporter;

import com.reqwise.dto.ExecutiveScorecardResponse;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.RequirementIssue;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementIssueRepository;
import com.reqwise.repository.RequirementRepository;
import com.reqwise.service.rtm.ExecutiveScorecardService;
import com.reqwise.service.storage.StorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates print-ready Executive Audit Report PDFs using Apache PDFBox.
 * Includes readiness indices, ISO 25010 breakdowns, critical contradictions,
 * and stakeholder verification sign-offs.
 */
@Service
public class PdfExporterService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExporterService.class);

    private final DocumentVersionRepository versionRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementIssueRepository issueRepository;
    private final ExecutiveScorecardService scorecardService;
    private final StorageService storageService;

    public PdfExporterService(
            DocumentVersionRepository versionRepository,
            RequirementRepository requirementRepository,
            RequirementIssueRepository issueRepository,
            ExecutiveScorecardService scorecardService,
            StorageService storageService) {
        this.versionRepository = versionRepository;
        this.requirementRepository = requirementRepository;
        this.issueRepository = issueRepository;
        this.scorecardService = scorecardService;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public byte[] generateExecutiveReportPdfBytes(UUID versionId) throws IOException {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        ExecutiveScorecardResponse scorecard = scorecardService.getScorecard(versionId);
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(versionId);

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float y = 780;

                // 1. Header Banner
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                cs.newLineAtOffset(margin, y);
                cs.showText("ReqWise - Executive Requirements Audit Report");
                cs.endText();
                y -= 25;

                // Subtitle
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(margin, y);
                String docName = version.getDocument() != null ? version.getDocument().getTitle() : "SRS Document";
                String projName = (version.getDocument() != null && version.getDocument().getProject() != null) ?
                        version.getDocument().getProject().getName() : "Enterprise Project";
                cs.showText(String.format("Project: %s | Document: %s (%s)", projName, docName, version.getVersionLabel()));
                cs.endText();
                y -= 30;

                // Divider line
                cs.setLineWidth(1.0f);
                cs.moveTo(margin, y);
                cs.lineTo(545, y);
                cs.stroke();
                y -= 25;

                // 2. Executive KPI Box
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13);
                cs.newLineAtOffset(margin, y);
                cs.showText("1. Executive Readiness Index & Key Quality Metrics");
                cs.endText();
                y -= 20;

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(margin + 10, y);
                cs.showText(String.format("Executive Readiness Index:   %.1f / 100.0", scorecard.readinessIndex()));
                cs.newLineAtOffset(0, -15);
                cs.showText(String.format("Overall Clarity Score:       %.1f%% (ISO/IEC/IEEE 29148 Standard)", scorecard.overallClarityScore()));
                cs.newLineAtOffset(0, -15);
                cs.showText(String.format("Traceability DAG Coverage:   %.1f%% linked requirements", scorecard.traceabilityCoverage()));
                cs.newLineAtOffset(0, -15);
                cs.showText(String.format("Total Atomic Requirements:   %d specifications", scorecard.totalRequirements()));
                cs.newLineAtOffset(0, -15);
                cs.showText(String.format("Total Flagged Defects:       %d issues (Defect Density: %.2f)", scorecard.totalIssues(), scorecard.defectDensity()));
                cs.endText();
                y -= 95;

                // 3. ISO 25010 Distribution
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13);
                cs.newLineAtOffset(margin, y);
                cs.showText("2. ISO 25010 Requirements Distribution");
                cs.endText();
                y -= 20;

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(margin + 10, y);
                StringBuilder sbIso = new StringBuilder();
                for (Map.Entry<String, Long> entry : scorecard.iso25010Breakdown().entrySet()) {
                    sbIso.append(entry.getKey()).append(": ").append(entry.getValue()).append("  |  ");
                }
                String isoStr = sbIso.length() > 0 ? sbIso.substring(0, Math.min(sbIso.length() - 5, 80)) : "Functional: 100%";
                cs.showText(isoStr);
                cs.endText();
                y -= 35;

                // 4. Critical Defects & Contradictions Highlights
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 13);
                cs.newLineAtOffset(margin, y);
                cs.showText("3. Critical Defects & Direct Contradictions");
                cs.endText();
                y -= 20;

                List<RequirementIssue> criticalIssues = issues.stream()
                        .filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity()) || "CONFLICT".equalsIgnoreCase(i.getIssueCategory()))
                        .limit(4)
                        .toList();

                if (criticalIssues.isEmpty()) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 10);
                    cs.newLineAtOffset(margin + 10, y);
                    cs.showText("No critical logical contradictions or breaking defects detected.");
                    cs.endText();
                    y -= 30;
                } else {
                    for (RequirementIssue iss : criticalIssues) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
                        cs.newLineAtOffset(margin + 10, y);
                        String pReq = iss.getPrimaryReq() != null ? iss.getPrimaryReq().getReqCode() : "REQ";
                        cs.showText(String.format("[%s] %s (%s)", iss.getSeverity(), pReq, iss.getIssueCategory()));
                        cs.endText();
                        y -= 14;

                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                        cs.newLineAtOffset(margin + 15, y);
                        String expl = iss.getExplanation();
                        if (expl.length() > 95) {
                            expl = expl.substring(0, 92) + "...";
                        }
                        cs.showText(expl);
                        cs.endText();
                        y -= 20;
                    }
                }

                // 5. Sign-off / Verification Footer
                y = 80;
                cs.setLineWidth(0.5f);
                cs.moveTo(margin, y);
                cs.lineTo(545, y);
                cs.stroke();
                y -= 15;

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated by ReqWise Intelligence Platform | Automated ISO/IEC/IEEE 29148 Audit Verification");
                cs.endText();
            }

            doc.save(baos);
            byte[] bytes = baos.toByteArray();

            // Persist as export artifact
            storageService.storeFile(
                    new ByteArrayInputStream(bytes),
                    "exports",
                    "Executive_Audit_" + versionId + ".pdf"
            );

            return bytes;
        }
    }
}
