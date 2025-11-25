package com.securedoc.extractor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_document", columnList = "document_id")
})
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String username; // 사용자가 삭제되어도 기록 유지

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActionType action;

    @Column(length = 100)
    private String resource; // 리소스 유형 (DOCUMENT, USER 등)

    @Column(name = "document_id", length = 50)
    private String documentId; // 문서 ID (있는 경우)

    @Column(columnDefinition = "TEXT")
    private String details; // 상세 정보 (JSON 형식)

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 200)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 20)
    private String status; // SUCCESS, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // 실패 시 에러 메시지

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }

    public enum ActionType {
        // 인증 관련
        LOGIN,
        LOGOUT,
        REGISTER,

        // 문서 관련
        DOCUMENT_UPLOAD,
        DOCUMENT_VIEW,
        DOCUMENT_DOWNLOAD,
        DOCUMENT_EDIT,
        DOCUMENT_DELETE,
        DOCUMENT_EXPORT,

        // 기타
        UNAUTHORIZED_ACCESS,
        SETTINGS_CHANGE
    }

    // 편의 생성자
    public AuditLog(User user, ActionType action, String resource, String details) {
        this.user = user;
        this.username = user != null ? user.getUsername() : "anonymous";
        this.action = action;
        this.resource = resource;
        this.details = details;
        this.status = "SUCCESS";
    }
}
