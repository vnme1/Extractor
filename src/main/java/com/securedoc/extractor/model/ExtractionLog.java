package com.securedoc.extractor.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class ExtractionLog {

    private String timestamp;
    private String level;
    private String message;

    public ExtractionLog(String level, String message) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.level = level;
        this.message = message;
    }
}