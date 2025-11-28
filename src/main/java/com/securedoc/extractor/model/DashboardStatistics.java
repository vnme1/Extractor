package com.securedoc.extractor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Table(name = "dashboard_statistics")
@Data
@NoArgsConstructor
public class DashboardStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_year", nullable = false)
    private int year;

    @Column(name = "stat_month", nullable = false)
    private int month;

    @Column(nullable = false)
    private long totalDocumentsUploaded = 0;

    @Column(nullable = false)
    private long totalDocumentsCompleted = 0;

    @Column(nullable = false)
    private long totalDocumentsWithErrors = 0;

    @Column(nullable = false)
    private double averageConfidence = 0.0;

    @Column(nullable = false, updatable = false)
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

    public DashboardStatistics(int year, int month) {
        this.year = year;
        this.month = month;
    }

    public static DashboardStatistics forMonth(YearMonth yearMonth) {
        return new DashboardStatistics(yearMonth.getYear(), yearMonth.getMonthValue());
    }
}
