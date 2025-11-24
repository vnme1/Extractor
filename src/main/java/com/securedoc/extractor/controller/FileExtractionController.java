package com.securedoc.extractor.controller;

import com.securedoc.extractor.model.Document;
import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.service.DocumentService;
import com.securedoc.extractor.service.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/extract")
@RequiredArgsConstructor
@Slf4j
@Validated
public class FileExtractionController {

    private static final String UPLOAD_DIR = "uploaded_files/";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = { ".pdf" };

    private final PdfExtractionService pdfExtractionService;
    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<ExtractionResult> uploadAndExtract(
            @RequestParam("file") @NotNull(message = "파일이 필요합니다") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return createErrorResponse("업로드할 파일이 없습니다", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return createErrorResponse("파일 크기가 50MB를 초과합니다", HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return createErrorResponse("파일명이 유효하지 않습니다", HttpStatus.BAD_REQUEST);
        }

        if (!isValidFileExtension(originalFilename)) {
            return createErrorResponse("PDF 파일만 업로드 가능합니다", HttpStatus.BAD_REQUEST);
        }

        // Content-Type 검증 추가
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("pdf")) {
            return createErrorResponse("PDF 파일만 업로드 가능합니다", HttpStatus.BAD_REQUEST);
        }

        Path tempFilePath = null;

        try {
            tempFilePath = saveTempFile(file);

            ExtractionResult result = pdfExtractionService.processPdfFile(tempFilePath.toFile());

            documentService.saveExtractionResult(result);

            log.info("파일 처리 완료: {}", originalFilename);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("파일 저장 실패: {}", originalFilename, e);
            return createErrorResponse("파일 저장 중 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR);

        } catch (Exception e) {
            log.error("파일 처리 실패: {}", originalFilename, e);
            return createErrorResponse("파일 처리 중 오류 발생: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            cleanupTempFile(tempFilePath);
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentService.findAllDocuments());
    }

    @GetMapping("/documents/recent")
    public ResponseEntity<List<Document>> getRecentDocuments() {
        return ResponseEntity.ok(documentService.findRecentDocuments());
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<Document> getDocument(@PathVariable String docId) {
        return documentService.findByDocId(docId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Path saveTempFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String uniqueFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(uniqueFilename);

        file.transferTo(filePath);
        log.debug("임시 파일 저장: {}", filePath);

        return filePath;
    }

    private void cleanupTempFile(Path filePath) {
        if (filePath != null && Files.exists(filePath)) {
            try {
                Files.delete(filePath);
                log.debug("임시 파일 삭제: {}", filePath);
            } catch (IOException e) {
                log.warn("임시 파일 삭제 실패: {}", filePath, e);
            }
        }
    }

    private boolean isValidFileExtension(String filename) {
        if (filename == null) {
            return false;
        }

        String lowerFilename = filename.toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lowerFilename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ExtractionResult> createErrorResponse(String message, HttpStatus status) {
        ExtractionResult errorResult = new ExtractionResult();
        errorResult.setStatus("error");
        errorResult.setConfidence(0.0);
        errorResult.addLog("ERROR", message);

        return new ResponseEntity<>(errorResult, status);
    }
}