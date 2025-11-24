package com.securedoc.extractor.model;

public class ExtractionLog {

    private String timestamp;
    private String level; // INFO, WARN, ERROR, FATAL
    private String message;

    // TODO: 생성자, Getter, Setter 메소드를 추가하세요.

    // 1. 생성자 (Constructor)
    public ExtractionLog(String level, String message) {
        this.timestamp = java.time.LocalTime.now().toString();
        this.level = level;
        this.message = message;
    }

    // 2. Getter 및 Setter
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}