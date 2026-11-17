package com.reqwise.controller;

import com.reqwise.service.exporter.PdfExporterService;
import com.reqwise.service.exporter.XlsxExporterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Report Exporters", description = "Export multi-sheet Excel RTM workbooks and print-ready PDF executive audit packages")
public class ExportController {

    private final XlsxExporterService xlsxExporterService;
    private final PdfExporterService pdfExporterService;

    public ExportController(XlsxExporterService xlsxExporterService, PdfExporterService pdfExporterService) {
        this.xlsxExporterService = xlsxExporterService;
        this.pdfExporterService = pdfExporterService;
    }

    @GetMapping("/versions/{versionId}/export/xlsx")
    @Operation(summary = "Download multi-tab Excel workbook containing RTM, Defects, and Executive Scorecard")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable UUID versionId) throws IOException {
        byte[] data = xlsxExporterService.generateRtmWorkbookBytes(versionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"RTM_Audit_" + versionId + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @GetMapping("/versions/{versionId}/export/pdf")
    @Operation(summary = "Download print-ready Executive Audit Report PDF with KPI scorecard and defect breakdown")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID versionId) throws IOException {
        byte[] data = pdfExporterService.generateExecutiveReportPdfBytes(versionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Executive_Audit_" + versionId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
