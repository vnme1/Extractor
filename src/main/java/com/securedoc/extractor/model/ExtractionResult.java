package com.securedoc.extractor.model;

import java.util.ArrayList;
import java.util.List;

public class ExtractionResult {

    // 문서 및 추출 결과의 핵심 정보
    private String docId;
    private String fileName;
    private int totalPages;
    private String rawText;

    // 구조화된 데이터 필드
    private String contractorA; // 발주사 (갑)
    private String contractorB; // 수주사 (을)
    private String startDate; // 계약 시작일 (YYYY-MM-DD)
    private String endDate; // 계약 종료일 (YYYY-MM-DD)
    private long amount; // 총 계약 금액
    private double confidence; // 추출 신뢰도 (0.0 ~ 1.0)

    // 처리 상태 및 로그
    private String status; // processing, completed, error
    private List<ExtractionLog> logs;

    // TODO: 생성자, Getter, Setter 메소드를 추가하세요.

    // 1. 생성자 (Constructor)
    public ExtractionResult() {
        this.logs = new ArrayList<>();
        this.status = "processing";
        this.confidence = 0.0;
        // docId는 실제 파일 처리 시점에 생성되거나 할당될 수 있습니다.
    }

    // 로그 추가 헬퍼 메소드
    public void addLog(String level, String message) {
        this.logs.add(new ExtractionLog(level, message));
    }

    // 2. Getter 및 Setter

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getContractorA() {
        return contractorA;
    }

    public void setContractorA(String contractorA) {
        this.contractorA = contractorA;
    }

    public String getContractorB() {
        return contractorB;
    }

    public void setContractorB(String contractorB) {
        this.contractorB = contractorB;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ExtractionLog> getLogs() {
        return logs;
    }

    public void setLogs(List<ExtractionLog> logs) {
        this.logs = logs;
    }
}