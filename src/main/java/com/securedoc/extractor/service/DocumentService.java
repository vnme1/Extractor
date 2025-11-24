package com.securedoc.extractor.service;

import com.securedoc.extractor.model.Document;
import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;

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

        log.info("문서 저장 완료: {}", document.getDocId());
        return documentRepository.save(document);
    }

    public Optional<Document> findByDocId(String docId) {
        return documentRepository.findByDocId(docId);
    }

    public List<Document> findAllDocuments() {
        return documentRepository.findAll();
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