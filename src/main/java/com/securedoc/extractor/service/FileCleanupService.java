package com.securedoc.extractor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

/**
 * 업로드된 파일 자동 정리 서비스
 */
@Service
@Slf4j
public class FileCleanupService {

    private static final String UPLOAD_DIR = "uploaded_files/";
    private static final long MAX_FILE_AGE_HOURS = 24; // 24시간 이상 된 파일 삭제

    /**
     * 매 시간마다 오래된 파일 정리 (초기 지연 1시간)
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 3600000) // 1시간마다
    public void cleanupOldFiles() {
        log.info("파일 정리 작업 시작...");

        Path uploadPath = Paths.get(UPLOAD_DIR);

        // 디렉토리가 존재하지 않으면 스킵
        if (!Files.exists(uploadPath)) {
            log.debug("업로드 디렉토리가 존재하지 않습니다: {}", UPLOAD_DIR);
            return;
        }

        int deletedCount = 0;
        int errorCount = 0;

        try (Stream<Path> files = Files.walk(uploadPath)) {
            Instant cutoffTime = Instant.now().minus(MAX_FILE_AGE_HOURS, ChronoUnit.HOURS);

            for (Path file : files.filter(Files::isRegularFile).toList()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    Instant fileTime = attrs.creationTime().toInstant();

                    // 파일이 24시간 이상 된 경우 삭제
                    if (fileTime.isBefore(cutoffTime)) {
                        Files.delete(file);
                        deletedCount++;
                        log.debug("오래된 파일 삭제: {}", file.getFileName());
                    }

                } catch (IOException e) {
                    errorCount++;
                    log.warn("파일 삭제 실패: {}", file.getFileName(), e);
                }
            }

            if (deletedCount > 0) {
                log.info("파일 정리 완료: {} 개 파일 삭제, {} 개 실패", deletedCount, errorCount);
            } else {
                log.debug("삭제할 오래된 파일 없음");
            }

        } catch (IOException e) {
            log.error("파일 정리 작업 중 오류 발생", e);
        }
    }

    /**
     * 매일 자정에 빈 디렉토리 정리
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 00:00:00
    public void cleanupEmptyDirectories() {
        log.info("빈 디렉토리 정리 작업 시작...");

        Path uploadPath = Paths.get(UPLOAD_DIR);

        if (!Files.exists(uploadPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(uploadPath)) {
            paths.filter(Files::isDirectory)
                 .filter(dir -> !dir.equals(uploadPath))
                 .forEach(dir -> {
                     try {
                         if (isDirEmpty(dir)) {
                             Files.delete(dir);
                             log.debug("빈 디렉토리 삭제: {}", dir);
                         }
                     } catch (IOException e) {
                         log.warn("디렉토리 삭제 실패: {}", dir, e);
                     }
                 });

        } catch (IOException e) {
            log.error("디렉토리 정리 작업 중 오류 발생", e);
        }
    }

    /**
     * 디렉토리가 비어있는지 확인
     */
    private boolean isDirEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findFirst().isEmpty();
        }
    }
}
