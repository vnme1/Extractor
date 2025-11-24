package com.securedoc.extractor.repository;

import com.securedoc.extractor.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByDocId(String docId);

    List<Document> findByStatus(String status);

    List<Document> findTop10ByOrderByCreatedAtDesc();
}