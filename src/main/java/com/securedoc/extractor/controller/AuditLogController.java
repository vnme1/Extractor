package com.securedoc.extractor.controller;

import com.securedoc.extractor.model.AuditLog;
import com.securedoc.extractor.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 모든 감사 로그 조회 (관리자 전용)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AuditLog> logs = auditLogService.findAll(pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 특정 사용자의 로그 조회 (관리자 또는 본인)
     */
    @GetMapping("/user/{username}")
    @PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
    public ResponseEntity<Page<AuditLog>> getUserLogs(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.findByUsername(username, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 현재 사용자의 최근 활동 조회
     */
    @GetMapping("/my-activity")
    public ResponseEntity<List<AuditLog>> getMyRecentActivity() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();

        List<AuditLog> logs = auditLogService.findRecentUserActivity(username);
        return ResponseEntity.ok(logs);
    }

    /**
     * 특정 문서의 로그 조회
     */
    @GetMapping("/document/{docId}")
    public ResponseEntity<List<AuditLog>> getDocumentLogs(@PathVariable String docId) {
        List<AuditLog> logs = auditLogService.findByDocumentId(docId);
        return ResponseEntity.ok(logs);
    }

    /**
     * 액션 타입별 로그 조회 (관리자 전용)
     */
    @GetMapping("/action/{action}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getLogsByAction(
            @PathVariable AuditLog.ActionType action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.findByAction(action, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 기간별 로그 조회 (관리자 전용)
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.findByDateRange(start, end, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 실패한 활동 조회 (관리자 전용)
     */
    @GetMapping("/failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getFailedActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditLogService.findFailedActivities(pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 최근 로그 조회 (관리자 전용)
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getRecentLogs() {
        List<AuditLog> logs = auditLogService.findRecentLogs();
        return ResponseEntity.ok(logs);
    }
}
