package com.securedoc.extractor.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ExtractionResult {

    private String docId;
    private String fileName;
    private int totalPages;
    private String rawText;

    private String contractorA;
    private String contractorB;
    private String startDate;
    private String endDate;
    private long amount = -1; // -1은 추출 실패를 의미
    private double confidence = 0.0;

    private String status = "processing";
    private String filePath;
    private List<ExtractionLog> logs = new ArrayList<>();

    public void addLog(String level, String message) {
        this.logs.add(new ExtractionLog(level, message));
    }
}