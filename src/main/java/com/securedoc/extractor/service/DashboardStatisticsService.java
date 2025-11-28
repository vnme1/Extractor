package com.securedoc.extractor.service;

import com.securedoc.extractor.model.DashboardStatistics;
import com.securedoc.extractor.repository.DashboardStatisticsRepository;
import com.securedoc.extractor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsService {

    private final DashboardStatisticsRepository statisticsRepository;

    /**
     * 문서 업로드 시 통계 증가
     */
    @Transactional
    public void incrementDocumentUploaded(double confidence) {
        YearMonth currentMonth = YearMonth.now();
        DashboardStatistics stats = getOrCreateStatistics(currentMonth);

        stats.setTotalDocumentsUploaded(stats.getTotalDocumentsUploaded() + 1);

        // 평균 신뢰도 업데이트 (누적 평균 계산)
        long totalDocs = stats.getTotalDocumentsUploaded();
        double currentAvg = stats.getAverageConfidence();
        double newAvg = ((currentAvg * (totalDocs - 1)) + confidence) / totalDocs;
        stats.setAverageConfidence(newAvg);

        statisticsRepository.save(stats);
        log.debug("문서 업로드 통계 증가: {}년 {}월", currentMonth.getYear(), currentMonth.getMonthValue());
    }

    /**
     * 문서 완료 시 통계 증가
     */
    @Transactional
    public void incrementDocumentCompleted() {
        YearMonth currentMonth = YearMonth.now();
        DashboardStatistics stats = getOrCreateStatistics(currentMonth);

        stats.setTotalDocumentsCompleted(stats.getTotalDocumentsCompleted() + 1);

        statisticsRepository.save(stats);
        log.debug("문서 완료 통계 증가: {}년 {}월", currentMonth.getYear(), currentMonth.getMonthValue());
    }

    /**
     * 문서 오류 시 통계 증가
     */
    @Transactional
    public void incrementDocumentWithError() {
        YearMonth currentMonth = YearMonth.now();
        DashboardStatistics stats = getOrCreateStatistics(currentMonth);

        stats.setTotalDocumentsWithErrors(stats.getTotalDocumentsWithErrors() + 1);

        statisticsRepository.save(stats);
        log.debug("문서 오류 통계 증가: {}년 {}월", currentMonth.getYear(), currentMonth.getMonthValue());
    }

    /**
     * 해당 월의 통계 가져오기 (없으면 생성)
     */
    public DashboardStatistics getOrCreateStatistics(YearMonth yearMonth) {
        Optional<DashboardStatistics> statsOpt = statisticsRepository.findByYearAndMonth(
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );

        return statsOpt.orElseGet(() -> {
            DashboardStatistics newStats = DashboardStatistics.forMonth(yearMonth);
            return statisticsRepository.save(newStats);
        });
    }

    /**
     * 현재 월 통계 조회
     */
    public DashboardStatistics getCurrentMonthStatistics() {
        YearMonth currentMonth = YearMonth.now();
        return getOrCreateStatistics(currentMonth);
    }

    /**
     * 전체 누적 통계 계산
     */
    public long getTotalDocumentsUploaded() {
        return statisticsRepository.findAll().stream()
                .mapToLong(DashboardStatistics::getTotalDocumentsUploaded)
                .sum();
    }

    public long getTotalDocumentsCompleted() {
        return statisticsRepository.findAll().stream()
                .mapToLong(DashboardStatistics::getTotalDocumentsCompleted)
                .sum();
    }

    public long getTotalDocumentsWithErrors() {
        return statisticsRepository.findAll().stream()
                .mapToLong(DashboardStatistics::getTotalDocumentsWithErrors)
                .sum();
    }

    public double getOverallAverageConfidence() {
        return statisticsRepository.findAll().stream()
                .filter(s -> s.getTotalDocumentsUploaded() > 0)
                .mapToDouble(s -> s.getAverageConfidence() * s.getTotalDocumentsUploaded())
                .sum() / Math.max(1, getTotalDocumentsUploaded());
    }
}
