package com.securedoc.extractor.service;

import com.securedoc.extractor.model.AuditLog;
import com.securedoc.extractor.model.Document;
import com.securedoc.extractor.repository.AuditLogRepository;
import com.securedoc.extractor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 대시보드 통계 데이터
     */
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 이번 달 문서 수
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        long monthlyDocuments = documentRepository.countByCreatedAtAfter(startOfMonth);

        // 전체 문서 수
        long totalDocuments = documentRepository.count();

        // 평균 정확도 계산
        List<Document> allDocuments = documentRepository.findAll();
        double avgAccuracy = allDocuments.stream()
                .mapToDouble(Document::getConfidence)
                .average()
                .orElse(0.0);

        // 상태별 문서 수
        long pendingCount = allDocuments.stream()
                .filter(doc -> "pending".equalsIgnoreCase(doc.getStatus()))
                .count();

        long errorCount = allDocuments.stream()
                .filter(doc -> "error".equalsIgnoreCase(doc.getStatus()))
                .count();

        stats.put("monthlyDocuments", monthlyDocuments);
        stats.put("totalDocuments", totalDocuments);
        stats.put("averageAccuracy", Math.round(avgAccuracy * 100.0) / 100.0);
        stats.put("pendingDocuments", pendingCount);
        stats.put("errorDocuments", errorCount);

        return stats;
    }

    /**
     * 월별 문서 처리 추이 (최근 6개월)
     */
    public Map<String, Object> getMonthlyTrends() {
        Map<String, Object> trends = new HashMap<>();

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDateTime startOfMonth = month.atDay(1).atStartOfDay();
            LocalDateTime endOfMonth = month.atEndOfMonth().atTime(23, 59, 59);

            long count = documentRepository.countByCreatedAtBetween(startOfMonth, endOfMonth);

            labels.add(month.format(formatter));
            data.add(count);
        }

        trends.put("labels", labels);
        trends.put("data", data);

        return trends;
    }

    /**
     * 최근 활동 로그
     */
    public Map<String, Object> getRecentActivities(int limit) {
        Map<String, Object> result = new HashMap<>();

        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByTimestampDesc();

        List<Map<String, Object>> activities = logs.stream()
                .limit(limit)
                .map(log -> {
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("id", log.getId());
                    activity.put("username", log.getUsername());
                    activity.put("action", log.getAction().toString());
                    activity.put("resource", log.getResource());
                    activity.put("details", log.getDetails());
                    activity.put("timestamp", log.getTimestamp().toString());
                    activity.put("status", log.getStatus());
                    return activity;
                })
                .collect(Collectors.toList());

        result.put("activities", activities);
        result.put("total", activities.size());

        return result;
    }
}
