package com.securedoc.extractor.repository;

import com.securedoc.extractor.model.DashboardStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DashboardStatisticsRepository extends JpaRepository<DashboardStatistics, Long> {

    Optional<DashboardStatistics> findByYearAndMonth(int year, int month);

    List<DashboardStatistics> findByOrderByYearDescMonthDesc();

    List<DashboardStatistics> findTop6ByOrderByYearDescMonthDesc();
}
