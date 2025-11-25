package com.securedoc.extractor.service;

import com.securedoc.extractor.model.ExtractionResult;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
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

    // 여러 금액 패턴 시도
    private static final Pattern AMOUNT_PATTERN_1 = Pattern.compile(
            "(총\\s*계약\\s*금액은?|금액은?|계약금은?).*?[₩\\(]?([0-9,]+)\\)?\\s*원",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_PATTERN_2 = Pattern.compile(
            "₩\\s*([0-9,]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_PATTERN_3 = Pattern.compile(
            "\\(\\s*([0-9,]+)\\s*원?\\)",
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
        PDDocument document = null;
        try {
            document = PDDocument.load(file);

            // 암호화된 PDF 체크
            if (document.isEncrypted()) {
                throw new IOException("암호화된 PDF 파일은 처리할 수 없습니다");
            }

            result.setTotalPages(document.getNumberOfPages());
            result.addLog("INFO", "PDF 로드 완료 (총 " + document.getNumberOfPages() + " 페이지)");

            // 페이지 수가 0인 경우 체크
            if (document.getNumberOfPages() == 0) {
                throw new IOException("유효한 페이지가 없는 PDF 파일입니다");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // 추출된 텍스트가 비어있는 경우 OCR 시도
            if (text == null || text.trim().isEmpty() || text.trim().length() < 100) {
                result.addLog("INFO", "텍스트 추출 실패. OCR을 시도합니다...");
                text = extractTextUsingOCR(document, result);
            }

            return text != null ? text : "";

        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new IOException("암호화된 PDF 파일은 처리할 수 없습니다", e);
        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("expected='PDF'") ||
                    e.getMessage().contains("not a valid PDF") ||
                    e.getMessage().contains("Error: End-of-File"))) {
                throw new IOException("손상되었거나 유효하지 않은 PDF 파일입니다", e);
            }
            throw e;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log.warn("PDF 문서 닫기 실패", e);
                }
            }
        }
    }

    private String extractTextUsingOCR(PDDocument document, ExtractionResult result) {
        StringBuilder ocrText = new StringBuilder();

        try {
            Tesseract tesseract = new Tesseract();

            // Tesseract 데이터 경로 설정 (여러 경로 시도)
            String[] possiblePaths = {
                    "C:/Program Files/Tesseract-OCR/tessdata",
                    "C:/Program Files (x86)/Tesseract-OCR/tessdata",
                    "/usr/share/tesseract-ocr/4.00/tessdata",
                    "/usr/share/tessdata",
                    "./tessdata"
            };

            boolean tessDataFound = false;
            for (String path : possiblePaths) {
                File tessDataDir = new File(path);
                if (tessDataDir.exists()) {
                    tesseract.setDatapath(path);
                    tessDataFound = true;
                    result.addLog("INFO", "Tesseract 데이터 경로: " + path);
                    break;
                }
            }

            if (!tessDataFound) {
                result.addLog("ERROR", "Tesseract가 설치되지 않았거나 언어 데이터를 찾을 수 없습니다.");
                result.addLog("INFO", "Tesseract 설치 방법: https://github.com/tesseract-ocr/tesseract");
                return "";
            }

            // 언어 설정 시도 (한글만, 영어만, 둘 다 순서로 시도)
            String[] languageOptions = { "kor+eng", "eng", "kor" };
            boolean languageSet = false;

            for (String lang : languageOptions) {
                try {
                    tesseract.setLanguage(lang);
                    languageSet = true;
                    result.addLog("INFO", "OCR 언어 설정: " + lang);
                    break;
                } catch (Exception e) {
                    log.debug("언어 {} 설정 실패", lang);
                }
            }

            if (!languageSet) {
                result.addLog("ERROR", "OCR 언어 데이터를 로드할 수 없습니다. kor.traineddata 또는 eng.traineddata 파일이 필요합니다.");
                return "";
            }

            tesseract.setPageSegMode(1); // 자동 페이지 분할
            tesseract.setOcrEngineMode(1); // LSTM 엔진 사용

            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            // 최대 10페이지까지만 OCR (성능 고려)
            int maxPages = Math.min(pageCount, 10);

            for (int page = 0; page < maxPages; page++) {
                try {
                    result.addLog("INFO", String.format("페이지 %d/%d OCR 처리 중...", page + 1, maxPages));

                    // PDF 페이지를 이미지로 변환 (300 DPI)
                    BufferedImage image = renderer.renderImageWithDPI(page, 300);

                    // OCR 수행
                    String pageText = tesseract.doOCR(image);

                    if (pageText != null && !pageText.trim().isEmpty()) {
                        ocrText.append(pageText).append("\n");
                    }

                } catch (Exception e) {
                    log.warn("페이지 " + (page + 1) + " OCR 실패", e);
                    result.addLog("WARN", "페이지 " + (page + 1) + " OCR 실패: " + e.getMessage());
                }
            }

            if (pageCount > maxPages) {
                result.addLog("INFO", String.format("성능을 위해 처음 %d 페이지만 OCR 처리했습니다.", maxPages));
            }

            String finalText = ocrText.toString().trim();
            if (!finalText.isEmpty()) {
                result.addLog("INFO", "OCR로 텍스트 추출 완료 (" + finalText.length() + " chars)");
            } else {
                result.addLog("WARN", "OCR로 텍스트를 추출할 수 없습니다.");
            }

            return finalText;

        } catch (Exception e) {
            log.error("OCR 처리 중 오류 발생", e);
            result.addLog("ERROR", "OCR 실패: " + e.getMessage());
            return "";
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
        // 패턴 1: "총 계약 금액은 ... (₩100,000,000)" 형식
        Matcher matcher1 = AMOUNT_PATTERN_1.matcher(text);
        if (matcher1.find()) {
            String amountStr = matcher1.group(2).replaceAll("[,\\s₩]", "");
            if (tryParseAmount(amountStr, result, "패턴1")) {
                return;
            }
        }

        // 패턴 2: "₩100,000,000" 형식
        Matcher matcher2 = AMOUNT_PATTERN_2.matcher(text);
        if (matcher2.find()) {
            String amountStr = matcher2.group(1).replaceAll("[,\\s]", "");
            if (tryParseAmount(amountStr, result, "패턴2")) {
                return;
            }
        }

        // 패턴 3: "(100,000,000)" 괄호 안의 숫자
        Matcher matcher3 = AMOUNT_PATTERN_3.matcher(text);
        if (matcher3.find()) {
            String amountStr = matcher3.group(1).replaceAll("[,\\s]", "");
            if (tryParseAmount(amountStr, result, "패턴3")) {
                return;
            }
        }

        result.addLog("WARN", "계약 금액 정보를 찾을 수 없습니다");
    }

    private boolean tryParseAmount(String amountStr, ExtractionResult result, String patternName) {
        try {
            long amount = Long.parseLong(amountStr);
            // 너무 작거나 큰 값 필터링 (1원 ~ 1조원)
            if (amount < 1 || amount > 1_000_000_000_000L) {
                result.addLog("WARN", String.format("%s: 금액이 범위를 벗어남 (%s)", patternName, amountStr));
                return false;
            }
            result.setAmount(amount);
            result.addLog("INFO", String.format("%s로 계약 금액 추출: %,d원", patternName, amount));
            return true;
        } catch (NumberFormatException e) {
            result.addLog("WARN", String.format("%s: 금액 파싱 실패 (%s)", patternName, amountStr));
            return false;
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
        // 금액이 0 이상이면 추출된 것으로 간주 (0원 계약도 유효할 수 있음)
        if (result.getAmount() >= 0)
            filledFields++;

        double confidence = (double) filledFields / totalFields;
        result.setConfidence(Math.round(confidence * 100.0) / 100.0);

        result.addLog("INFO", String.format("신뢰도 계산: %.0f%% (%d/%d 필드)",
                confidence * 100, filledFields, totalFields));
    }
}