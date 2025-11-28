# SecureDoc Extractor - 기업용 문서 자동 분석 시스템

## 📋 프로젝트 개요

SecureDoc Extractor는 기업에서 다루는 **계약서, 보고서, NDA, 견적서** 같은 PDF 문서를 로컬에서 자동 분석하고 핵심 데이터를 구조화하여 보여주는 시스템입니다.

### 핵심 기능

- ✅ PDF 텍스트 자동 추출 (PDFBox 사용)
- ✅ 정규식 기반 엔티티 자동 추출
- ✅ 구조화된 데이터 자동 생성
- ✅ 로컬 처리 (보안 중시)
- ✅ 문서 이력 관리 (H2 데이터베이스)
- ✅ CSV 내보내기 기능

---

## 🛠️ 기술 스택

### Backend

- **Spring Boot 3.2.0**
- **Java 17**
- **Apache PDFBox 2.0.30**
- **H2 Database**
- **Lombok**

### Frontend

- **HTML5 / JavaScript**
- **Tailwind CSS**
- **Font Awesome**

---

## 🚀 시작하기

### 1. 사전 요구사항

```bash
# Java 17 이상
java -version

# Maven 3.6 이상
mvn -version
```

### 2. 프로젝트 클론

```bash
git clone https://github.com/your-repo/securedoc-extractor.git
cd securedoc-extractor
```

### 3. 빌드 및 실행

```bash
# Maven 빌드
mvn clean package

# 애플리케이션 실행
mvn spring-boot:run
```

또는

```bash
# JAR 파일 실행
java -jar target/extractor-1.0.0.jar
```

### 4. 브라우저에서 접속

```
http://localhost:18339
```

---

## 📂 프로젝트 구조

```
securedoc-extractor/
├── src/
│   ├── main/
│   │   ├── java/com/securedoc/extractor/
│   │   │   ├── controller/        # REST API 컨트롤러
│   │   │   ├── service/           # 비즈니스 로직
│   │   │   ├── model/             # 데이터 모델
│   │   │   ├── repository/        # JPA Repository
│   │   │   └── ExtractorApplication.java
│   │   └── resources/
│   │       ├── static/            # 프론트엔드 파일
│   │       │   ├── index.html
│   │       │   └── script.js
│   │       └── application.properties
│   └── test/                      # 테스트 코드
├── pom.xml
└── README.md
```

---

## 🔧 핵심 API 엔드포인트

### 1. 파일 업로드 및 추출

```http
POST /api/extract/upload
Content-Type: multipart/form-data

파라미터: file (PDF 파일)
```

**응답 예시:**

```json
{
  "docId": "DOC-1234567890",
  "fileName": "contract.pdf",
  "totalPages": 5,
  "contractorA": "주식회사 ABC",
  "contractorB": "(주) XYZ",
  "startDate": "2025-01-01",
  "endDate": "2025-12-31",
  "amount": 55000000,
  "confidence": 0.85,
  "status": "completed",
  "logs": [...]
}
```

### 2. 최근 문서 조회

```http
GET /api/extract/documents/recent
```

### 3. 특정 문서 조회

```http
GET /api/extract/documents/{docId}
```

---

## 📊 추출 항목

### 자동 추출되는 데이터

| 항목            | 설명             | 예시         |
| --------------- | ---------------- | ------------ |
| **발주사(갑)**  | 계약서상 갑 회사 | 주식회사 ABC |
| **수주사(을)**  | 계약서상 을 회사 | (주) XYZ     |
| **계약 시작일** | 계약 시작 날짜   | 2025-01-01   |
| **계약 종료일** | 계약 종료 날짜   | 2025-12-31   |
| **계약 금액**   | 총 계약 금액     | 55,000,000원 |

---

## 🔒 보안 특징

- **로컬 처리 전용**: 모든 PDF 처리는 로컬에서만 수행
- **외부 전송 없음**: 문서가 외부 서버로 전송되지 않음
- **임시 파일 자동 삭제**: 처리 후 임시 파일 즉시 삭제
- **AI 모델 불필요**: 정규식 기반 로컬 처리

---

## 🧪 테스트

```bash
# 단위 테스트 실행
mvn test

# 통합 테스트 실행
mvn verify
```

---

## 📦 배포

### JAR 파일 생성

```bash
mvn clean package
```

### Docker 배포 (옵션)

```bash
# Dockerfile 생성 예정
docker build -t securedoc-extractor .
docker run -p 18339:18339 securedoc-extractor
```

---

## 🔄 향후 개선 계획

- [ ] OCR 기능 추가 (이미지 PDF 지원)
- [ ] 다양한 문서 유형 지원 (NDA, 견적서)
- [ ] 문서 비교 기능
- [ ] 조항 누락 자동 검출
- [ ] Excel 내보내기 기능
- [ ] 다국어 지원 (영문 계약서)

---


## 📞 문의

문제가 발생하거나 문의사항이 있으시면 Issue를 생성해주세요.
