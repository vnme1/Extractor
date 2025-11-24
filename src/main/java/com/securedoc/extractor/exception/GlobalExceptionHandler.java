package com.securedoc.extractor.exception;

import com.securedoc.extractor.model.ExtractionResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;

/**
 * 전역 예외 처리 핸들러
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 파일 크기 초과 예외
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ExtractionResult> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.error("파일 크기 초과: {}", ex.getMessage());
        return createErrorResponse("파일 크기가 50MB를 초과합니다", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * 멀티파트 요청 예외
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ExtractionResult> handleMultipartException(MultipartException ex) {
        log.error("파일 업로드 오류: {}", ex.getMessage());
        return createErrorResponse("파일 업로드 중 오류가 발생했습니다", HttpStatus.BAD_REQUEST);
    }

    /**
     * Bean Validation 예외
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExtractionResult> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("입력 검증 실패: {}", ex.getMessage());
        return createErrorResponse("입력값이 유효하지 않습니다", HttpStatus.BAD_REQUEST);
    }

    /**
     * IO 예외 (파일 읽기/쓰기 오류)
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ExtractionResult> handleIOException(IOException ex) {
        log.error("파일 처리 오류: {}", ex.getMessage(), ex);

        String message = "파일 처리 중 오류가 발생했습니다";
        if (ex.getMessage() != null && ex.getMessage().contains("PDF")) {
            message = "손상되었거나 유효하지 않은 PDF 파일입니다";
        }

        return createErrorResponse(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * NullPointerException - null 참조 오류
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ExtractionResult> handleNullPointer(NullPointerException ex) {
        log.error("Null 참조 오류: {}", ex.getMessage(), ex);
        return createErrorResponse("요청 처리 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * IllegalArgumentException - 잘못된 인자
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExtractionResult> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("잘못된 요청: {}", ex.getMessage());
        return createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * 정적 리소스를 찾을 수 없을 때 (favicon.ico, 삭제된 파일 등)
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        // 로그를 DEBUG 레벨로 낮춤 (favicon.ico 등은 정상적인 404)
        log.debug("정적 리소스 없음: {}", ex.getMessage());
        return ResponseEntity.notFound().build();
    }

    /**
     * 모든 기타 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExtractionResult> handleGenericException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return createErrorResponse("서버에서 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 에러 응답 생성 유틸리티
     */
    private ResponseEntity<ExtractionResult> createErrorResponse(String message, HttpStatus status) {
        ExtractionResult errorResult = new ExtractionResult();
        errorResult.setStatus("error");
        errorResult.setConfidence(0.0);
        errorResult.addLog("ERROR", message);

        return new ResponseEntity<>(errorResult, status);
    }
}
