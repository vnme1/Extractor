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
}