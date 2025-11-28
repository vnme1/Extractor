package com.securedoc.extractor.controller;

import com.securedoc.extractor.model.Document;
import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.service.DocumentService;
import com.securedoc.extractor.service.ExcelExportService;
import com.securedoc.extractor.service.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private static final String STORED_DIR = "stored_documents/";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = { ".pdf" };

    private final PdfExtractionService pdfExtractionService;
    private final DocumentService documentService;
    private final ExcelExportService excelExportService;

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
        Path storedFilePath = null;

        try {
            tempFilePath = saveTempFile(file);

            ExtractionResult result = pdfExtractionService.processPdfFile(tempFilePath.toFile());

            // PDF 파일을 영구 저장소에 저장
            storedFilePath = saveStoredFile(file, result.getDocId());
            result.setFilePath(storedFilePath.toString());

            documentService.saveExtractionResult(result);

            log.info("파일 처리 완료: {}", originalFilename);
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            log.error("파일 저장 실패: {}", originalFilename, e);
            // 저장 실패 시 영구 파일도 삭제
            cleanupTempFile(storedFilePath);
            return createErrorResponse("파일 저장 중 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR);

        } catch (Exception e) {
            log.error("파일 처리 실패: {}", originalFilename, e);
            // 처리 실패 시 영구 파일도 삭제
            cleanupTempFile(storedFilePath);
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

    /**
     * 페이지네이션을 지원하는 문서 조회
     * @param page 페이지 번호 (0부터 시작, 기본값: 0)
     * @param size 페이지 크기 (기본값: 20)
     * @param sort 정렬 기준 (기본값: createdAt,desc)
     */
    @GetMapping("/documents/paginated")
    public ResponseEntity<Page<Document>> getDocumentsWithPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Document> documents = documentService.findDocumentsWithPagination(pageable);

        return ResponseEntity.ok(documents);
    }

    @GetMapping("/documents/recent")
    public ResponseEntity<List<Document>> getRecentDocuments() {
        return ResponseEntity.ok(documentService.findRecentDocuments());
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<Document> getDocument(@PathVariable String docId) {
        // 숫자인 경우 ID로 조회, 문자열인 경우 docId로 조회
        try {
            Long id = Long.parseLong(docId);
            return documentService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (NumberFormatException e) {
            return documentService.findByDocId(docId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
    }

    /**
     * 문서 정보 업데이트 (검증 완료 시)
     */
    @PutMapping("/documents/{id}")
    public ResponseEntity<Document> updateDocument(
            @PathVariable Long id,
            @RequestBody Document updates) {
        try {
            Document updated = documentService.updateDocument(id, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("문서 업데이트 실패: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 문서 재처리 요청
     */
    @PostMapping("/documents/{id}/reprocess")
    public ResponseEntity<?> reprocessDocument(@PathVariable Long id) {
        try {
            documentService.markForReprocessing(id);
            return ResponseEntity.ok().body("{\"message\": \"재처리 요청이 완료되었습니다\"}");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("문서 재처리 요청 실패: {}", id, e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"재처리 요청 중 오류 발생\"}");
        }
    }

    /**
     * PDF 파일 제공
     */
    @GetMapping("/documents/{id}/pdf")
    public ResponseEntity<ByteArrayResource> getDocumentPdf(@PathVariable Long id) {
        try {
            Document document = documentService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

            if (document.getFilePath() == null || document.getFilePath().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Path pdfPath = Paths.get(document.getFilePath());
            if (!Files.exists(pdfPath)) {
                log.error("PDF 파일이 존재하지 않음: {}", pdfPath);
                return ResponseEntity.notFound().build();
            }

            byte[] pdfData = Files.readAllBytes(pdfPath);
            ByteArrayResource resource = new ByteArrayResource(pdfData);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                            java.net.URLEncoder.encode(document.getFileName(), "UTF-8").replace("+", "%20"))
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfData.length)
                    .body(resource);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("PDF 파일 제공 실패: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 단일 문서 Excel 다운로드
     */
    @GetMapping("/documents/{docId}/export/excel")
    public ResponseEntity<ByteArrayResource> exportDocumentToExcel(@PathVariable String docId) {
        try {
            Document document = documentService.findByDocId(docId)
                    .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + docId));

            byte[] excelData = excelExportService.exportSingleDocumentToExcel(document);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = document.getFileName().replaceAll("\\.pdf$", "") + "_추출결과.xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                            java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Excel 내보내기 실패: {}", docId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 전체 문서 Excel 다운로드
     */
    @GetMapping("/documents/export/excel")
    public ResponseEntity<ByteArrayResource> exportAllDocumentsToExcel() {
        try {
            List<Document> documents = documentService.findAllDocuments();

            if (documents.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            byte[] excelData = excelExportService.exportMultipleDocumentsToExcel(documents);

            ByteArrayResource resource = new ByteArrayResource(excelData);

            String filename = "문서목록_" + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                            java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelData.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Excel 전체 내보내기 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 단일 문서 삭제
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String docId) {
        try {
            boolean deleted = documentService.deleteDocument(docId);

            if (deleted) {
                return ResponseEntity.ok().body("{\"message\": \"문서가 삭제되었습니다\", \"docId\": \"" + docId + "\"}");
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("문서 삭제 실패: {}", docId, e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"문서 삭제 중 오류 발생\"}");
        }
    }

    /**
     * 여러 문서 삭제
     */
    @DeleteMapping("/documents")
    public ResponseEntity<?> deleteDocuments(@RequestBody List<String> docIds) {
        try {
            if (docIds == null || docIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"삭제할 문서 ID가 필요합니다\"}");
            }

            int deletedCount = documentService.deleteDocuments(docIds);

            return ResponseEntity.ok()
                    .body("{\"message\": \"" + deletedCount + "개 문서가 삭제되었습니다\", \"count\": " + deletedCount + "}");

        } catch (Exception e) {
            log.error("여러 문서 삭제 실패", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"문서 삭제 중 오류 발생\"}");
        }
    }

    /**
     * 전체 문서 삭제 (관리자 전용)
     */
    @DeleteMapping("/documents/all")
    public ResponseEntity<?> deleteAllDocuments() {
        try {
            int deletedCount = documentService.deleteAllDocuments();

            return ResponseEntity.ok()
                    .body("{\"message\": \"전체 문서가 삭제되었습니다\", \"count\": " + deletedCount + "}");

        } catch (Exception e) {
            log.error("전체 문서 삭제 실패", e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"문서 삭제 중 오류 발생\"}");
        }
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

    private Path saveStoredFile(MultipartFile file, String docId) throws IOException {
        Path storedPath = Paths.get(STORED_DIR);
        if (!Files.exists(storedPath)) {
            Files.createDirectories(storedPath);
        }

        String storedFilename = docId + "_" + file.getOriginalFilename();
        Path filePath = storedPath.resolve(storedFilename);

        file.transferTo(filePath);
        log.info("영구 파일 저장: {}", filePath);

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