package com.securedoc.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_doc_id", columnList = "docId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String docId;

    @Column(nullable = false)
    private String fileName;

    private int totalPages;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    private String contractorA;
    private String contractorB;
    private String startDate;
    private String endDate;
    private Long amount;
    private Double confidence;

    @Column(nullable = false)
    private String status;

    @Column
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User owner;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Lombok이 자동 생성하는 getFileName()을 명시적으로 추가
    public String getFileName() {
        return this.fileName;
    }

    // JSON 직렬화를 위한 getter - amount를 contractAmount로 매핑
    public String getContractAmount() {
        return amount != null ? String.format("%,d", amount) : null;
    }

    // JSON 직렬화를 위한 getter - fileName을 filename으로도 접근 가능하게
    @com.fasterxml.jackson.annotation.JsonProperty("filename")
    public String getFilename() {
        return this.fileName;
    }
}