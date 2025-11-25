package com.securedoc.extractor.repository;

import com.securedoc.extractor.model.AuditLog;
import com.securedoc.extractor.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 사용자별 로그 조회
    Page<AuditLog> findByUser(User user, Pageable pageable);

    // 사용자명으로 로그 조회
    Page<AuditLog> findByUsername(String username, Pageable pageable);

    // 액션 타입별 조회
    Page<AuditLog> findByAction(AuditLog.ActionType action, Pageable pageable);

    // 문서별 로그 조회
    List<AuditLog> findByDocumentIdOrderByTimestampDesc(String documentId);

    // 특정 기간 내 로그 조회
    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 실패한 활동 조회
    Page<AuditLog> findByStatus(String status, Pageable pageable);

    // 최근 로그 조회
    List<AuditLog> findTop100ByOrderByTimestampDesc();

    // 특정 사용자의 최근 활동
    List<AuditLog> findTop20ByUsernameOrderByTimestampDesc(String username);
}
