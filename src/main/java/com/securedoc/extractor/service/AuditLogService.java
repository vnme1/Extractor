package com.securedoc.extractor.service;

import com.securedoc.extractor.model.AuditLog;
import com.securedoc.extractor.model.User;
import com.securedoc.extractor.repository.AuditLogRepository;
import com.securedoc.extractor.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * 감사 로그 기록 (비동기)
     */
    @Async
    @Transactional
    public void log(AuditLog.ActionType action, String resource, String details) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = null;
            String username = "anonymous";

            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
                user = userRepository.findByUsername(username).orElse(null);
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUser(user);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setDetails(details);
            auditLog.setStatus("SUCCESS");

            // HTTP 요청 정보 추가
            addRequestInfo(auditLog);

            auditLogRepository.save(auditLog);
            log.debug("감사 로그 기록: {} - {} - {}", username, action, resource);

        } catch (Exception e) {
            log.error("감사 로그 기록 실패", e);
        }
    }

    /**
     * 문서 관련 로그 기록
     */
    @Async
    @Transactional
    public void logDocument(AuditLog.ActionType action, String documentId, String details) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user = null;
            String username = "anonymous";

            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getPrincipal().equals("anonymousUser")) {
                username = authentication.getName();
                user = userRepository.findByUsername(username).orElse(null);
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUser(user);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setResource("DOCUMENT");
            auditLog.setDocumentId(documentId);
            auditLog.setDetails(details);
            auditLog.setStatus("SUCCESS");

            addRequestInfo(auditLog);

            auditLogRepository.save(auditLog);
            log.debug("문서 로그 기록: {} - {} - {}", username, action, documentId);

        } catch (Exception e) {
            log.error("문서 로그 기록 실패", e);
        }
    }

    /**
     * 실패한 활동 로그
     */
    @Async
    @Transactional
    public void logFailure(AuditLog.ActionType action, String resource, String errorMessage) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "anonymous";

            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setResource(resource);
            auditLog.setStatus("FAILED");
            auditLog.setErrorMessage(errorMessage);

            addRequestInfo(auditLog);

            auditLogRepository.save(auditLog);
            log.warn("실패 로그 기록: {} - {} - {}", username, action, errorMessage);

        } catch (Exception e) {
            log.error("실패 로그 기록 실패", e);
        }
    }

    /**
     * HTTP 요청 정보 추가
     */
    private void addRequestInfo(AuditLog auditLog) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            log.debug("HTTP 요청 정보 추가 실패", e);
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    // ===== 조회 메서드 =====

    /**
     * 모든 로그 조회 (페이지네이션)
     */
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * 사용자별 로그 조회
     */
    public Page<AuditLog> findByUsername(String username, Pageable pageable) {
        return auditLogRepository.findByUsername(username, pageable);
    }

    /**
     * 액션별 로그 조회
     */
    public Page<AuditLog> findByAction(AuditLog.ActionType action, Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }

    /**
     * 문서별 로그 조회
     */
    public List<AuditLog> findByDocumentId(String documentId) {
        return auditLogRepository.findByDocumentIdOrderByTimestampDesc(documentId);
    }

    /**
     * 기간별 로그 조회
     */
    public Page<AuditLog> findByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByTimestampBetween(start, end, pageable);
    }

    /**
     * 최근 로그 조회
     */
    public List<AuditLog> findRecentLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    /**
     * 사용자의 최근 활동 조회
     */
    public List<AuditLog> findRecentUserActivity(String username) {
        return auditLogRepository.findTop20ByUsernameOrderByTimestampDesc(username);
    }

    /**
     * 실패한 활동 조회
     */
    public Page<AuditLog> findFailedActivities(Pageable pageable) {
        return auditLogRepository.findByStatus("FAILED", pageable);
    }
}
