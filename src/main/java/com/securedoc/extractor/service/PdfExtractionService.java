package com.securedoc.extractor.service;

import com.securedoc.extractor.model.ExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfExtractionService {

    private static final Pattern CONTRACT_PARTY_PATTERN = Pattern.compile(
            "(주식회사|\\(주\\)|㈜)\\s*([가-힣A-Za-z0-9&\\s]{2,30})\\s*\\(이하\\s*[\"']?(갑|을)[\"']?\\s*이라\\s*한다\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(계약\\s*기간은?|기간은?).*?" +
                    "(\\d{4}[년/-]\\s*\\d{1,2}[월/-]?\\s*\\d{1,2}일?).*?" +
                    "(부터|から).*?" +
                    "(\\d{4}[년/-]\\s*\\d{1,2}[월/-]?\\s*\\d{1,2}일?).*?" +
                    "(까지|まで)",
            Pattern.DOTALL);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(총\\s*계약\\s*금액은?|금액은?|계약금은?)\\s*([^원]*?([0-9,]+)\\s*원)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KOREAN_NUMBER_PATTERN = Pattern.compile(
            "(일|이|삼|사|오|육|칠|팔|구)+(십|백|천|만|억|조)?원?");

    public ExtractionResult processPdfFile(File file) {
        ExtractionResult result = new ExtractionResult();
        result.setDocId("DOC-" + System.currentTimeMillis());
        result.setFileName(file.getName());
        result.addLog("INFO", "처리 시작: " + file.getName());

        try {
            String rawText = extractTextFromPdf(file, result);
            result.setRawText(rawText);
            result.addLog("INFO", "PDF 텍스트 추출 완료 (" + rawText.length() + " chars)");

            extractStructuredData(rawText, result);

            result.setStatus("completed");
            result.addLog("INFO", "추출 완료");

        } catch (Exception e) {
            log.error("PDF 처리 중 오류 발생", e);
            result.setStatus("error");
            result.setConfidence(0.0);
            result.addLog("ERROR", "추출 실패: " + e.getMessage());
        }

        return result;
    }

    private String extractTextFromPdf(File file, ExtractionResult result) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            result.setTotalPages(document.getNumberOfPages());
            result.addLog("INFO", "PDF 로드 완료 (총 " + document.getNumberOfPages() + " 페이지)");

            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private void extractStructuredData(String text, ExtractionResult result) {
        String normalizedText = normalizeText(text);

        extractContractParties(normalizedText, result);
        extractDates(normalizedText, result);
        extractAmount(normalizedText, result);

        calculateConfidence(result);
    }

    private String normalizeText(String text) {
        return text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void extractContractParties(String text, ExtractionResult result) {
        Matcher matcher = CONTRACT_PARTY_PATTERN.matcher(text);
        int count = 0;

        while (matcher.find() && count < 2) {
            String prefix = matcher.group(1);
            String companyName = matcher.group(2).trim();
            String role = matcher.group(3);

            String fullName = normalizeCompanyName(prefix, companyName);

            if ("갑".equals(role)) {
                result.setContractorA(fullName);
                result.addLog("INFO", "발주사(갑) 추출: " + fullName);
            } else if ("을".equals(role)) {
                result.setContractorB(fullName);
                result.addLog("INFO", "수주사(을) 추출: " + fullName);
            }
            count++;
        }

        if (count == 0) {
            result.addLog("WARN", "계약 당사자 정보를 찾을 수 없습니다");
        }
    }

    private String normalizeCompanyName(String prefix, String name) {
        prefix = prefix.replaceAll("\\s+", "");
        if (prefix.equals("㈜") || prefix.contains("(주)")) {
            return "(주)" + name;
        }
        return "주식회사 " + name;
    }

    private void extractDates(String text, ExtractionResult result) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(text);

        if (matcher.find()) {
            String startDateRaw = matcher.group(2).trim();
            String endDateRaw = matcher.group(4).trim();

            result.setStartDate(normalizeDate(startDateRaw));
            result.setEndDate(normalizeDate(endDateRaw));

            result.addLog("INFO", "계약 기간 추출: " + result.getStartDate() + " ~ " + result.getEndDate());
        } else {
            result.addLog("WARN", "계약 기간 정보를 찾을 수 없습니다");
        }
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("(\\d{4})[년/-]\\s*(\\d{1,2})[월/-]?\\s*(\\d{1,2})").matcher(dateStr);

        if (matcher.find()) {
            return String.format("%s-%02d-%02d",
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        }

        return dateStr;
    }

    private void extractAmount(String text, ExtractionResult result) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);

        if (matcher.find()) {
            String amountStr = matcher.group(3).replaceAll("[,\\s]", "");

            try {
                long amount = Long.parseLong(amountStr);
                result.setAmount(amount);
                result.addLog("INFO", "계약 금액 추출: " + String.format("%,d원", amount));
            } catch (NumberFormatException e) {
                result.addLog("WARN", "금액 파싱 실패: " + amountStr);
            }
        } else {
            result.addLog("WARN", "계약 금액 정보를 찾을 수 없습니다");
        }
    }

    private void calculateConfidence(ExtractionResult result) {
        int filledFields = 0;
        int totalFields = 5;

        if (result.getContractorA() != null && !result.getContractorA().isBlank())
            filledFields++;
        if (result.getContractorB() != null && !result.getContractorB().isBlank())
            filledFields++;
        if (result.getStartDate() != null && !result.getStartDate().isBlank())
            filledFields++;
        if (result.getEndDate() != null && !result.getEndDate().isBlank())
            filledFields++;
        if (result.getAmount() > 0)
            filledFields++;

        double confidence = (double) filledFields / totalFields;
        result.setConfidence(Math.round(confidence * 100.0) / 100.0);

        result.addLog("INFO", String.format("신뢰도 계산: %.0f%% (%d/%d 필드)",
                confidence * 100, filledFields, totalFields));
    }
}