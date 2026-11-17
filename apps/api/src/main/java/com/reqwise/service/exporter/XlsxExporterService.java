package com.reqwise.service.exporter;

import com.reqwise.dto.ExecutiveScorecardResponse;
import com.reqwise.dto.RtmGraphResponse;
import com.reqwise.dto.RtmNode;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Requirement;
import com.reqwise.model.RequirementIssue;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.RequirementIssueRepository;
import com.reqwise.repository.RequirementRepository;
import com.reqwise.service.rtm.ExecutiveScorecardService;
import com.reqwise.service.rtm.RtmLinkerService;
import com.reqwise.service.storage.StorageService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Multi-sheet Excel exporter for Requirement Traceability Matrix (RTM),
 * Quality Defects, and Executive Audit Scorecards.
 */
@Service
public class XlsxExporterService {

    private static final Logger logger = LoggerFactory.getLogger(XlsxExporterService.class);

    private final DocumentVersionRepository versionRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementIssueRepository issueRepository;
    private final RtmLinkerService rtmLinkerService;
    private final ExecutiveScorecardService scorecardService;
    private final StorageService storageService;

    public XlsxExporterService(
            DocumentVersionRepository versionRepository,
            RequirementRepository requirementRepository,
            RequirementIssueRepository issueRepository,
            RtmLinkerService rtmLinkerService,
            ExecutiveScorecardService scorecardService,
            StorageService storageService) {
        this.versionRepository = versionRepository;
        this.requirementRepository = requirementRepository;
        this.issueRepository = issueRepository;
        this.rtmLinkerService = rtmLinkerService;
        this.scorecardService = scorecardService;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public byte[] generateRtmWorkbookBytes(UUID versionId) throws IOException {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        List<Requirement> reqs = requirementRepository.findByVersionIdOrderByReqCodeAsc(versionId);
        List<RequirementIssue> issues = issueRepository.findByVersionIdOrderByCreatedAtDesc(versionId);
        RtmGraphResponse rtmGraph = rtmLinkerService.getRtmGraph(versionId);
        ExecutiveScorecardResponse scorecard = scorecardService.getScorecard(versionId);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);

            // ==========================================
            // Sheet 1: Requirement Traceability Matrix
            // ==========================================
            Sheet wsRtm = wb.createSheet("Traceability Matrix");
            String[] rtmHeaders = {
                    "Req ID", "Section Path", "Type", "Category", "Atomic Requirement",
                    "Clarity Score", "Upstream Source", "Downstream Verification / Link", "Traceability Status"
            };
            Row rtmHeaderRow = wsRtm.createRow(0);
            for (int i = 0; i < rtmHeaders.length; i++) {
                Cell cell = rtmHeaderRow.createCell(i);
                cell.setCellValue(rtmHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<UUID, RtmNode> nodeMap = new HashMap<>();
            for (RtmNode n : rtmGraph.nodes()) {
                nodeMap.put(n.id(), n);
            }

            Map<UUID, List<String>> downstreamMap = new HashMap<>();
            Map<UUID, List<String>> upstreamMap = new HashMap<>();
            for (var edge : rtmGraph.edges()) {
                downstreamMap.computeIfAbsent(edge.sourceId(), k -> new ArrayList<>()).add(edge.targetReqCode());
                upstreamMap.computeIfAbsent(edge.targetId(), k -> new ArrayList<>()).add(edge.sourceReqCode());
            }

            int rowIdx = 1;
            for (Requirement req : reqs) {
                Row row = wsRtm.createRow(rowIdx++);
                List<String> upLinks = upstreamMap.getOrDefault(req.getId(), List.of());
                List<String> downLinks = downstreamMap.getOrDefault(req.getId(), List.of());

                String upStr = upLinks.isEmpty() ? "ORPHAN (No Parent)" : String.join(", ", upLinks);
                String downStr = downLinks.isEmpty() ? "UNVERIFIED" : String.join(", ", downLinks);
                String status = (!upLinks.isEmpty() && !downLinks.isEmpty()) ? "COMPLETE" : "GAP DETECTED";

                row.createCell(0).setCellValue(req.getReqCode());
                row.createCell(1).setCellValue(req.getSectionPath() != null ? req.getSectionPath() : "General");
                row.createCell(2).setCellValue(req.getReqType());
                row.createCell(3).setCellValue(req.getNfrCategory() != null ? req.getNfrCategory() : "-");
                row.createCell(4).setCellValue(req.getNormalizedText() != null ? req.getNormalizedText() : req.getRawText());
                row.createCell(5).setCellValue(req.getClarityScore() != null ? req.getClarityScore().doubleValue() : 100.0);
                row.createCell(6).setCellValue(upStr);
                row.createCell(7).setCellValue(downStr);
                row.createCell(8).setCellValue(status);

                for (int i = 0; i < 9; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }
            wsRtm.createFreezePane(0, 1);

            // ==========================================
            // Sheet 2: Defects & Ambiguities
            // ==========================================
            Sheet wsIssues = wb.createSheet("Defects & Ambiguities");
            String[] issueHeaders = {
                    "Primary Req Code", "Related Req Code", "Category", "Severity",
                    "Flagged Phrase", "Explanation", "Suggested Rewrite", "Resolved"
            };
            Row issueHeaderRow = wsIssues.createRow(0);
            for (int i = 0; i < issueHeaders.length; i++) {
                Cell cell = issueHeaderRow.createCell(i);
                cell.setCellValue(issueHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            rowIdx = 1;
            for (RequirementIssue issue : issues) {
                Row row = wsIssues.createRow(rowIdx++);
                row.createCell(0).setCellValue(issue.getPrimaryReq() != null ? issue.getPrimaryReq().getReqCode() : "-");
                row.createCell(1).setCellValue(issue.getRelatedReq() != null ? issue.getRelatedReq().getReqCode() : "-");
                row.createCell(2).setCellValue(issue.getIssueCategory());
                row.createCell(3).setCellValue(issue.getSeverity());
                row.createCell(4).setCellValue(issue.getFlaggedPhrase() != null ? issue.getFlaggedPhrase() : "-");
                row.createCell(5).setCellValue(issue.getExplanation());
                row.createCell(6).setCellValue(issue.getSuggestedRewrite() != null ? issue.getSuggestedRewrite() : "-");
                row.createCell(7).setCellValue(Boolean.TRUE.equals(issue.getIsResolved()) ? "YES" : "NO");

                for (int i = 0; i < 8; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }
            wsIssues.createFreezePane(0, 1);

            // ==========================================
            // Sheet 3: Executive Scorecard
            // ==========================================
            Sheet wsScorecard = wb.createSheet("Executive Scorecard");
            Row scHeader = wsScorecard.createRow(0);
            scHeader.createCell(0).setCellValue("Metric");
            scHeader.createCell(1).setCellValue("Value");
            scHeader.getCell(0).setCellStyle(headerStyle);
            scHeader.getCell(1).setCellStyle(headerStyle);

            Object[][] scMetrics = {
                    {"Executive Readiness Index (0-100)", scorecard.readinessIndex()},
                    {"Overall Quality Clarity Score", scorecard.overallClarityScore() + "%"},
                    {"Traceability Graph Coverage", scorecard.traceabilityCoverage() + "%"},
                    {"Total Extracted Requirements", scorecard.totalRequirements()},
                    {"Total Quality Issues & Defects", scorecard.totalIssues()},
                    {"Defect Density (Issues / Req)", scorecard.defectDensity()}
            };

            for (int i = 0; i < scMetrics.length; i++) {
                Row r = wsScorecard.createRow(i + 1);
                r.createCell(0).setCellValue(scMetrics[i][0].toString());
                r.createCell(1).setCellValue(scMetrics[i][1].toString());
                r.getCell(0).setCellStyle(dataStyle);
                r.getCell(1).setCellStyle(dataStyle);
            }

            // Auto-size columns for all sheets
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                for (int c = 0; c < 9; c++) {
                    sheet.setColumnWidth(c, 22 * 256);
                }
            }

            wb.write(baos);
            byte[] bytes = baos.toByteArray();

            // Persist as export artifact
            storageService.storeFile(
                    new ByteArrayInputStream(bytes),
                    "exports",
                    "RTM_Audit_" + versionId + ".xlsx"
            );

            return bytes;
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // Dark Slate Fill (#1E293B)
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderTop(BorderStyle.HAIR);
        style.setBorderLeft(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        return style;
    }
}
