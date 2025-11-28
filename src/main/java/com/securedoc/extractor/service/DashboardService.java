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
    private final DashboardStatisticsService statisticsService;

    /**
     * 대시보드 통계 데이터 (누적 통계 사용)
     */
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 이번 달 업로드된 문서 수 (누적 통계에서)
        long monthlyDocuments = statisticsService.getCurrentMonthStatistics().getTotalDocumentsUploaded();

        // 전체 누적 업로드 문서 수 (삭제되어도 유지)
        long totalDocuments = statisticsService.getTotalDocumentsUploaded();

        // 전체 누적 평균 정확도 (삭제되어도 유지)
        double avgAccuracy = statisticsService.getOverallAverageConfidence();

        // 현재 보관 중인 문서의 상태별 카운트 (실시간)
        List<Document> currentDocuments = documentRepository.findAll();
        long pendingCount = currentDocuments.stream()
                .filter(doc -> "pending".equalsIgnoreCase(doc.getStatus()))
                .count();

        long errorCount = currentDocuments.stream()
                .filter(doc -> "error".equalsIgnoreCase(doc.getStatus()))
                .count();

        // 현재 보관 중인 문서 수 (실시간)
        long currentStoredDocuments = documentRepository.count();

        stats.put("monthlyDocuments", monthlyDocuments);
        stats.put("totalDocuments", totalDocuments);
        stats.put("averageAccuracy", Math.round(avgAccuracy * 100.0) / 100.0);
        stats.put("pendingDocuments", pendingCount);
        stats.put("errorDocuments", errorCount);
        stats.put("currentStoredDocuments", currentStoredDocuments);

        return stats;
    }

    /**
     * 월별 문서 처리 추이 (최근 6개월) - 누적 통계 사용
     */
    public Map<String, Object> getMonthlyTrends() {
        Map<String, Object> trends = new HashMap<>();

        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);

            // 누적 통계에서 해당 월의 업로드 수 가져오기
            long count = statisticsService.getCurrentMonthStatistics()
                    .getTotalDocumentsUploaded();

            // 만약 해당 월의 통계가 있다면 그 값을 사용, 없으면 0
            var stats = statisticsService.getOrCreateStatistics(month);
            count = stats.getTotalDocumentsUploaded();

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
