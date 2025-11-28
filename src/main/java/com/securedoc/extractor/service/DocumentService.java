package com.securedoc.extractor.service;

import com.securedoc.extractor.model.AuditLog;
import com.securedoc.extractor.model.Document;
import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.model.User;
import com.securedoc.extractor.repository.DocumentRepository;
import com.securedoc.extractor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DashboardStatisticsService statisticsService;

    @Transactional
    public Document saveExtractionResult(ExtractionResult result) {
        Document document = new Document();
        document.setDocId(result.getDocId());
        document.setFileName(result.getFileName());
        document.setTotalPages(result.getTotalPages());
        document.setRawText(result.getRawText());
        document.setContractorA(result.getContractorA());
        document.setContractorB(result.getContractorB());
        document.setStartDate(result.getStartDate());
        document.setEndDate(result.getEndDate());
        document.setAmount(result.getAmount());
        document.setConfidence(result.getConfidence());
        document.setStatus(result.getStatus());
        document.setFilePath(result.getFilePath());

        // 현재 로그인한 사용자를 소유자로 설정
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            String username = authentication.getName();
            User owner = userRepository.findByUsername(username)
                    .orElse(null);
            document.setOwner(owner);
            log.debug("문서 소유자 설정: {}", username);
        }

        log.info("문서 저장 완료: {}", document.getDocId());
        Document savedDocument = documentRepository.save(document);

        // 감사 로그 기록
        auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_UPLOAD, document.getDocId(),
                String.format("문서 업로드: %s (%d 페이지)", document.getFileName(), document.getTotalPages()));

        // 통계 업데이트
        statisticsService.incrementDocumentUploaded(result.getConfidence());
        if ("error".equals(result.getStatus())) {
            statisticsService.incrementDocumentWithError();
        }

        return savedDocument;
    }

    public Optional<Document> findByDocId(String docId) {
        Optional<Document> document = documentRepository.findByDocId(docId);

        // 문서 조회 로그
        if (document.isPresent()) {
            auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_VIEW, docId,
                    String.format("문서 조회: %s", document.get().getFileName()));
        }

        return document;
    }

    /**
     * ID로 문서 조회
     */
    public Optional<Document> findById(Long id) {
        Optional<Document> document = documentRepository.findById(id);

        // 문서 조회 로그
        if (document.isPresent()) {
            Document doc = document.get();
            auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_VIEW, doc.getDocId(),
                    String.format("문서 조회: %s", doc.getFileName()));
        }

        return document;
    }

    public List<Document> findAllDocuments() {
        return documentRepository.findAll();
    }

    /**
     * 페이지네이션을 사용한 문서 조회
     */
    public Page<Document> findDocumentsWithPagination(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }

    public List<Document> findRecentDocuments() {
        return documentRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateDocumentStatus(String docId, String status) {
        documentRepository.findByDocId(docId).ifPresent(doc -> {
            doc.setStatus(status);
            documentRepository.save(doc);
            log.info("문서 상태 업데이트: {} -> {}", docId, status);
        });
    }

    /**
     * 문서 정보 업데이트 (검증 완료 시)
     */
    @Transactional
    public Document updateDocument(Long id, Document updates) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        // 업데이트 가능한 필드만 수정
        if (updates.getContractorA() != null) {
            document.setContractorA(updates.getContractorA());
        }
        if (updates.getContractorB() != null) {
            document.setContractorB(updates.getContractorB());
        }
        if (updates.getStartDate() != null) {
            document.setStartDate(updates.getStartDate());
        }
        if (updates.getEndDate() != null) {
            document.setEndDate(updates.getEndDate());
        }
        if (updates.getStatus() != null) {
            document.setStatus(updates.getStatus());
        }

        // contractAmount는 문자열로 들어오므로 파싱 필요
        // 하지만 Document 모델에 setContractAmount가 없으므로 amount를 직접 설정
        // 프론트엔드에서 contractAmount를 보내면 무시됨 (amount 필드를 사용해야 함)

        Document saved = documentRepository.save(document);

        // 감사 로그 기록
        auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_EDIT, document.getDocId(),
                String.format("문서 검증 완료: %s", document.getFileName()));

        // 완료 상태로 변경된 경우 통계 업데이트
        if ("completed".equals(updates.getStatus())) {
            statisticsService.incrementDocumentCompleted();
        }

        log.info("문서 업데이트 완료: {}", document.getDocId());
        return saved;
    }

    /**
     * 문서 재처리 요청
     */
    @Transactional
    public void markForReprocessing(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

        document.setStatus("pending");
        documentRepository.save(document);

        // 감사 로그 기록
        auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_EDIT, document.getDocId(),
                String.format("문서 재처리 요청: %s", document.getFileName()));

        log.info("문서 재처리 요청: {}", document.getDocId());
    }

    /**
     * 단일 문서 삭제
     */
    @Transactional
    public boolean deleteDocument(String docId) {
        Optional<Document> documentOpt = documentRepository.findByDocId(docId);

        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();

            // 감사 로그 기록
            auditLogService.logDocument(AuditLog.ActionType.DOCUMENT_DELETE, docId,
                    String.format("문서 삭제: %s", document.getFileName()));

            // 실제 PDF 파일 삭제
            deletePhysicalFile(document.getFilePath());

            documentRepository.delete(document);
            log.info("문서 삭제 완료: {}", docId);
            return true;
        }

        log.warn("삭제할 문서를 찾을 수 없음: {}", docId);
        return false;
    }

    /**
     * 실제 파일 삭제 헬퍼 메서드
     */
    private void deletePhysicalFile(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.delete(path);
                    log.info("실제 파일 삭제 완료: {}", filePath);
                } else {
                    log.warn("실제 파일이 존재하지 않음: {}", filePath);
                }
            } catch (Exception e) {
                log.error("실제 파일 삭제 실패: {}", filePath, e);
            }
        }
    }

    /**
     * 여러 문서 삭제
     */
    @Transactional
    public int deleteDocuments(List<String> docIds) {
        int deletedCount = 0;

        for (String docId : docIds) {
            if (deleteDocument(docId)) {
                deletedCount++;
            }
        }

        log.info("{}/{} 문서 삭제 완료", deletedCount, docIds.size());
        return deletedCount;
    }

    /**
     * 전체 문서 삭제 (관리자 전용)
     */
    @Transactional
    public int deleteAllDocuments() {
        List<Document> allDocuments = documentRepository.findAll();
        int count = allDocuments.size();

        // 감사 로그 기록
        auditLogService.log(AuditLog.ActionType.DOCUMENT_DELETE, "ALL_DOCUMENTS",
                String.format("전체 문서 삭제: %d건", count));

        documentRepository.deleteAll();
        log.warn("전체 문서 삭제 완료: {}건", count);
        return count;
    }
}