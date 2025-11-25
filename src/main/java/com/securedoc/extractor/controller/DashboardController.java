package com.securedoc.extractor.controller;

import com.securedoc.extractor.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 대시보드 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            Map<String, Object> stats = dashboardService.getDashboardStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("대시보드 통계 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 월별 문서 처리 추이 (최근 6개월)
     */
    @GetMapping("/monthly-trends")
    public ResponseEntity<Map<String, Object>> getMonthlyTrends() {
        try {
            Map<String, Object> trends = dashboardService.getMonthlyTrends();
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("월별 추이 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 최근 활동 로그
     */
    @GetMapping("/recent-activities")
    public ResponseEntity<Map<String, Object>> getRecentActivities(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Map<String, Object> activities = dashboardService.getRecentActivities(limit);
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("최근 활동 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
