package com.securedoc.extractor.service;

import com.securedoc.extractor.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel 내보내기 서비스
 */
@Service
@Slf4j
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 단일 문서를 Excel로 내보내기
     */
    public byte[] exportSingleDocumentToExcel(Document document) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("문서 정보");

            // 스타일 생성
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int rowNum = 0;

            // 헤더
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"항목", "값"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 데이터
            addDataRow(sheet, rowNum++, "문서 ID", document.getDocId(), dataStyle);
            addDataRow(sheet, rowNum++, "파일명", document.getFileName(), dataStyle);
            addDataRow(sheet, rowNum++, "총 페이지", String.valueOf(document.getTotalPages()), dataStyle);
            addDataRow(sheet, rowNum++, "발주사 (갑)", document.getContractorA(), dataStyle);
            addDataRow(sheet, rowNum++, "수주사 (을)", document.getContractorB(), dataStyle);
            addDataRow(sheet, rowNum++, "계약 시작일", document.getStartDate(), dataStyle);
            addDataRow(sheet, rowNum++, "계약 종료일", document.getEndDate(), dataStyle);
            addDataRow(sheet, rowNum++, "계약 금액",
                document.getAmount() != null && document.getAmount() >= 0
                    ? String.format("%,d원", document.getAmount())
                    : "추출 실패",
                dataStyle);
            addDataRow(sheet, rowNum++, "신뢰도",
                document.getConfidence() != null
                    ? String.format("%.0f%%", document.getConfidence() * 100)
                    : "0%",
                dataStyle);
            addDataRow(sheet, rowNum++, "상태", document.getStatus(), dataStyle);
            addDataRow(sheet, rowNum++, "생성일시",
                document.getCreatedAt() != null
                    ? document.getCreatedAt().format(DATE_FORMATTER)
                    : "",
                dataStyle);

            // 열 너비 자동 조정
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.setColumnWidth(1, sheet.getColumnWidth(1) + 2000); // 약간 여유 공간

            // ByteArray로 변환
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 여러 문서를 Excel로 내보내기
     */
    public byte[] exportMultipleDocumentsToExcel(List<Document> documents) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("문서 목록");

            // 스타일 생성
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int rowNum = 0;

            // 헤더
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {
                "문서 ID", "파일명", "발주사(갑)", "수주사(을)",
                "시작일", "종료일", "계약금액", "신뢰도", "상태", "생성일시"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 데이터
            for (Document doc : documents) {
                Row row = sheet.createRow(rowNum++);

                createCell(row, 0, doc.getDocId(), dataStyle);
                createCell(row, 1, doc.getFileName(), dataStyle);
                createCell(row, 2, doc.getContractorA(), dataStyle);
                createCell(row, 3, doc.getContractorB(), dataStyle);
                createCell(row, 4, doc.getStartDate(), dataStyle);
                createCell(row, 5, doc.getEndDate(), dataStyle);
                createCell(row, 6,
                    doc.getAmount() != null && doc.getAmount() >= 0
                        ? String.format("%,d원", doc.getAmount())
                        : "",
                    dataStyle);
                createCell(row, 7,
                    doc.getConfidence() != null
                        ? String.format("%.0f%%", doc.getConfidence() * 100)
                        : "0%",
                    dataStyle);
                createCell(row, 8, doc.getStatus(), dataStyle);
                createCell(row, 9,
                    doc.getCreatedAt() != null
                        ? doc.getCreatedAt().format(DATE_FORMATTER)
                        : "",
                    dataStyle);
            }

            // 열 너비 자동 조정
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            // ByteArray로 변환
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void addDataRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "");
        valueCell.setCellStyle(style);
    }

    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
}
