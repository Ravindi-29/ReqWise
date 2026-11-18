package com.reqwise.controller;

import com.reqwise.dto.DocumentResponse;
import com.reqwise.dto.DocumentVersionResponse;
import com.reqwise.model.Document;
import com.reqwise.model.DocumentVersion;
import com.reqwise.model.Project;
import com.reqwise.repository.DocumentRepository;
import com.reqwise.repository.DocumentVersionRepository;
import com.reqwise.repository.ProjectRepository;
import com.reqwise.service.parser.ParsedBlock;
import com.reqwise.service.pipeline.PipelineOrchestrator;
import com.reqwise.service.pipeline.SseNotificationService;
import com.reqwise.service.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Documents & Ingestion", description = "SRS Document Upload, Parsing & Streaming Progress")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ProjectRepository projectRepository;
    private final StorageService storageService;
    private final PipelineOrchestrator pipelineOrchestrator;
    private final SseNotificationService sseNotificationService;

    public DocumentController(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            ProjectRepository projectRepository,
            StorageService storageService,
            PipelineOrchestrator pipelineOrchestrator,
            SseNotificationService sseNotificationService) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.projectRepository = projectRepository;
        this.storageService = storageService;
        this.pipelineOrchestrator = pipelineOrchestrator;
        this.sseNotificationService = sseNotificationService;
    }

    @PostMapping(value = "/projects/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an SRS specification (PDF or DOCX) and begin ingestion pipeline")
    public ResponseEntity<DocumentVersionResponse> uploadDocument(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "versionLabel", defaultValue = "v1.0") String versionLabel,
            @RequestParam(value = "existingDocumentId", required = false) UUID existingDocumentId) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is missing");
        }

        String fileType;
        String lower = originalFilename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            fileType = "PDF";
        } else if (lower.endsWith(".docx")) {
            fileType = "DOCX";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .pdf and .docx files are supported");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        Document document;
        int versionNumber = 1;

        if (existingDocumentId != null) {
            document = documentRepository.findById(existingDocumentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Existing document not found"));
            List<DocumentVersion> existingVersions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(existingDocumentId);
            if (!existingVersions.isEmpty()) {
                versionNumber = existingVersions.get(0).getVersionNumber() + 1;
            }
        } else {
            String docTitle = (title != null && !title.trim().isEmpty())
                    ? title.trim()
                    : originalFilename.replaceAll("\\.[^.]+$", "");
            document = documentRepository.saveAndFlush(new Document(project, docTitle));
        }

        // Store file on local disk
        String storedPath;
        try {
            storedPath = storageService.storeFile(file.getInputStream(), "raw_documents", originalFilename);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file", e);
        }

        // Create version
        DocumentVersion version = new DocumentVersion(
                document,
                versionNumber,
                versionLabel,
                storedPath,
                fileType
        );
        DocumentVersion savedVersion = versionRepository.saveAndFlush(version);

        // Trigger async parsing pipeline (entity is already committed & flushed)
        pipelineOrchestrator.executePipeline(savedVersion.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentVersionResponse.fromEntity(savedVersion));
    }

    @GetMapping("/documents/{documentId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get document details and version history")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return ResponseEntity.ok(DocumentResponse.fromEntity(document));
    }

    @GetMapping("/versions/{versionId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get version details and processing status")
    public ResponseEntity<DocumentVersionResponse> getVersion(@PathVariable UUID versionId) {
        DocumentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found"));
        return ResponseEntity.ok(DocumentVersionResponse.fromEntity(version));
    }

    @GetMapping(value = "/versions/{versionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time Server-Sent Events (SSE) processing progress")
    public SseEmitter streamVersionProgress(@PathVariable UUID versionId) {
        if (!versionRepository.existsById(versionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found");
        }
        return sseNotificationService.subscribe(versionId);
    }

    @GetMapping("/versions/{versionId}/blocks")
    @Transactional(readOnly = true)
    @Operation(summary = "Get layout-parsed structural blocks and bounding boxes for a document version")
    public ResponseEntity<List<ParsedBlock>> getParsedBlocks(@PathVariable UUID versionId) {
        List<ParsedBlock> blocks = pipelineOrchestrator.getParsedBlocks(versionId);
        return ResponseEntity.ok(blocks);
    }
}
