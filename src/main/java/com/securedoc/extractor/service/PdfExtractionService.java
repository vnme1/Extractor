package com.securedoc.extractor.service;

import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.model.ExtractionLog;
import org.springframework.stereotype.Service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractionService {

    // --- 정규식 패턴 정의 ---

    // 1. 당사자(갑/을) 추출
    private static final Pattern CONTRACT_PARTY_PATTERN = Pattern.compile(
            "(주식회사|\\(주\\))\\s*([가-힣A-Za-z0-9\\s]{2,20})\\s*\\(이하\\s*\"(갑|을)\"\\s*이라\\s*한다\\)",
            Pattern.CASE_INSENSITIVE);

    // 2. 날짜 추출 (계약 기간) - '부터'와 '까지' 사이의 날짜 형식을 캡처
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(계약\\s*기간은|기간은).*?((?:\\d{4}년.*?\\d{1,2}일)|(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})).*?부터.*?((?:\\d{4}년.*?\\d{1,2}일)|(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})).*?까지",
            Pattern.DOTALL);

    // 3. 금액 추출 - "총 계약 금액은" 이후의 내용을 찾음
    private static final Pattern AMOUNT_CLAUSE_PATTERN = Pattern.compile(
            "(총\\s*계약\\s*금액은|금액은)\\s*(.*?)(원\\s*정|으로\\s*하며)", Pattern.DOTALL);

    // --- 메인 처리 메소드 ---

    public ExtractionResult processPdfFile(File storedFile) {

        List<ExtractionLog> logs = new ArrayList<>();
        ExtractionResult result = new ExtractionResult();

        logs.add(new ExtractionLog("INFO", "Processing started for file: " + storedFile.getName()));

        String rawText = "";
        try {
            // 문서 식별 정보 설정
            result.setFileName(storedFile.getName());
            result.setDocId("DOC-" + storedFile.lastModified());

            // 1. PDF 텍스트 추출 코어 로직 실행
            rawText = runPdfBoxExtraction(storedFile, logs, result);
            result.setRawText(rawText); // 추출된 텍스트를 결과 객체에 저장
            logs.add(new ExtractionLog("INFO", "PDF Text Extracted (Length: " + rawText.length() + " chars)"));

            // 2. 정규식 및 정규화 로직 호출
            runRuleBasedExtraction(rawText, logs, result);

            result.setStatus("completed");

        } catch (Exception e) {
            logs.add(new ExtractionLog("ERROR", "Extraction failed: " + e.getMessage()));
            result.setStatus("error");
            result.setConfidence(0.0);
            result.setRawText(rawText.isEmpty() ? "텍스트 추출 실패" : rawText); // 실패해도 텍스트가 있다면 저장
        }

        result.setLogs(logs);
        return result;
    }

    // --- PDFBox 텍스트 추출 ---

    private String runPdfBoxExtraction(File file, List<ExtractionLog> logs, ExtractionResult result)
            throws IOException {
        PDDocument document = null;
        String extractedText = "";

        try {
            document = PDDocument.load(file);
            logs.add(new ExtractionLog("INFO", "PDF Document loaded successfully."));

            int totalPages = document.getNumberOfPages();
            result.setTotalPages(totalPages);
            logs.add(new ExtractionLog("INFO", "Total pages: " + totalPages));

            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);

        } catch (IOException e) {
            logs.add(new ExtractionLog("ERROR", "PDFBox text extraction failed: " + e.getMessage()));
            throw e;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    logs.add(new ExtractionLog("WARN", "Failed to close PDDocument: " + e.getMessage()));
                }
            }
        }

        return extractedText;
    }

    // --- 정규식 기반 엔티티 추출 및 정규화 ---

    private void runRuleBasedExtraction(String text, List<ExtractionLog> logs, ExtractionResult result) {
        // 텍스트 전처리 (줄바꿈 및 불필요한 공백 정리)
        String normalizedText = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ");

        extractContractParties(normalizedText, logs, result);
        extractDates(normalizedText, logs, result);
        extractAmount(normalizedText, logs, result);

        logs.add(new ExtractionLog("INFO", "Rule-based extraction pipeline finished."));

        // 간단한 신뢰도 계산 (모든 핵심 필드가 채워졌는지 확인)
        int filledFields = 0;
        if (result.getContractorA() != null)
            filledFields++;
        if (result.getContractorB() != null)
            filledFields++;
        if (result.getStartDate() != null)
            filledFields++;
        if (result.getEndDate() != null)
            filledFields++;
        if (result.getAmount() > 0)
            filledFields++;

        result.setConfidence(Math.round(((double) filledFields / 5.0) * 100.0) / 100.0);
    }

    private void extractContractParties(String text, List<ExtractionLog> logs, ExtractionResult result) {
        Matcher partyMatcher = CONTRACT_PARTY_PATTERN.matcher(text);
        int partyCount = 0;

        while (partyMatcher.find() && partyCount < 2) {
            String companyName = partyMatcher.group(2).trim(); // 회사명
            String role = partyMatcher.group(3); // 갑 또는 을

            // 주식회사 또는 (주) 제거 및 정규화는 추후 DB 저장 시점에서 할 수 있습니다. 여기서는 원본에 가깝게 추출
            String companyPrefix = partyMatcher.group(1);
            String fullCompanyName = (companyPrefix.contains("(주)") ? "(주)" : "주식회사") + " " + companyName;

            if ("갑".equals(role)) {
                result.setContractorA(fullCompanyName);
                logs.add(new ExtractionLog("INFO", "Regex pattern [CONTRACT_PARTY_A] matched: " + fullCompanyName));
            } else if ("을".equals(role)) {
                result.setContractorB(fullCompanyName);
                logs.add(new ExtractionLog("INFO", "Regex pattern [CONTRACT_PARTY_B] matched: " + fullCompanyName));
            }
            partyCount++;
        }
    }

    private void extractDates(String text, List<ExtractionLog> logs, ExtractionResult result) {
        Matcher dateRangeMatcher = DATE_RANGE_PATTERN.matcher(text);

        if (dateRangeMatcher.find()) {
            String startDateRaw = dateRangeMatcher.group(2).trim();
            String endDateRaw = dateRangeMatcher.group(3).trim();

            String startDate = normalizeDate(startDateRaw);
            String endDate = normalizeDate(endDateRaw);

            result.setStartDate(startDate);
            result.setEndDate(endDate);

            logs.add(new ExtractionLog("INFO",
                    "Regex pattern [DATE_RANGE] matched. Start: " + startDateRaw + ", End: " + endDateRaw));
            logs.add(new ExtractionLog("INFO", "Date format normalized to YYYY-MM-DD."));
        }
    }

    private void extractAmount(String text, List<ExtractionLog> logs, ExtractionResult result) {
        Matcher amountMatcher = AMOUNT_CLAUSE_PATTERN.matcher(text);

        if (amountMatcher.find()) {
            String amountStrRaw = amountMatcher.group(2).trim();

            // 금액 부분에서 쉼표가 포함된 숫자 금액 패턴 찾기 (예: 55,000,000)
            Matcher numericMatcher = Pattern.compile("([0-9,]+)").matcher(amountStrRaw);

            if (numericMatcher.find()) {
                String numericAmountStr = numericMatcher.group(1);
                long amount = normalizeNumericAmount(numericAmountStr);
                result.setAmount(amount);
                logs.add(new ExtractionLog("INFO", "Money format extracted: " + numericAmountStr));
                logs.add(new ExtractionLog("INFO", "Money format normalized to integer."));
            } else {
                logs.add(new ExtractionLog("WARN", "Numeric amount not found in the amount clause."));
            }
        }
    }

    // --- 데이터 정규화 헬퍼 메소드 ---

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty())
            return null;

        // YYYY년 MM월 DD일 형식 처리
        Matcher matcher = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(dateStr);
        if (matcher.find()) {
            return String.format("%s-%02d-%02d",
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        }

        // YYYY/MM/DD 또는 YYYY-MM-DD 형식 처리
        dateStr = dateStr.replaceAll("[^0-9]", "-");
        dateStr = dateStr.replaceAll("-+", "-");
        dateStr = dateStr.replaceAll("^-|-$", "");

        String[] parts = dateStr.split("-");
        if (parts.length == 3 && parts[0].length() == 4) {
            return String.format("%s-%02d-%02d",
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }

        return dateStr;
    }

    private long normalizeNumericAmount(String numericAmount) {
        if (numericAmount == null || numericAmount.trim().isEmpty()) {
            return 0L;
        }
        String cleanString = numericAmount.replaceAll("[^0-9]", "");
        try {
            return Long.parseLong(cleanString);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}