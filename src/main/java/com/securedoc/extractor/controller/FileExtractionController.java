package com.securedoc.extractor.controller;

import com.securedoc.extractor.model.ExtractionLog;
import com.securedoc.extractor.model.ExtractionResult;
import com.securedoc.extractor.service.PdfExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/extract")
public class FileExtractionController {

    // 파일을 임시 저장할 로컬 디렉토리
    private static final String UPLOAD_DIR = "uploaded_files/";

    private final PdfExtractionService pdfExtractionService;

    // Service 생성자 주입
    public FileExtractionController(PdfExtractionService pdfExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ExtractionResult> uploadAndExtract(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            ExtractionResult errorResult = new ExtractionResult();
            errorResult.setStatus("error");
            errorResult.setLogs(List.of(new ExtractionLog("ERROR", "업로드할 파일이 없습니다.")));
            return new ResponseEntity<>(errorResult, HttpStatus.BAD_REQUEST);
        }

        Path filePath = null; // 오류 해결을 위해 블록 밖에서 선언

        try {
            // 1. 파일 저장 로직
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFileName = file.getOriginalFilename();
            // 고유 파일명 생성
            String storedFileName = System.currentTimeMillis() + "_" + originalFileName;

            filePath = uploadPath.resolve(storedFileName);
            file.transferTo(filePath);

            File storedFile = filePath.toFile();

            // 2. Service 호출 및 추출 실행
            ExtractionResult result = pdfExtractionService.processPdfFile(storedFile);

            // 3. 파일 처리 후 임시 파일을 삭제
            if (storedFile.exists()) {
                Files.delete(filePath);
                System.out.println("임시 파일 삭제 완료: " + storedFile.getName());
            }

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e) {
            System.err.println("파일 처리 중 오류 발생: " + e.getMessage());

            // 4. 에러 발생 시 파일 삭제 시도 (Clean-up)
            if (filePath != null && Files.exists(filePath)) {
                try {
                    Files.delete(filePath);
                    System.out.println("오류 발생 후 임시 파일 삭제 완료.");
                } catch (IOException deleteEx) {
                    System.err.println("임시 파일 삭제 실패: " + deleteEx.getMessage());
                }
            }

            // 에러 ExtractionResult 객체 생성 및 반환
            ExtractionResult errorResult = new ExtractionResult();
            errorResult.setStatus("error");
            errorResult.setConfidence(0.0);
            errorResult.setLogs(List.of(new ExtractionLog("FATAL", "서버 오류: " + e.getMessage())));
            return new ResponseEntity<>(errorResult, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}