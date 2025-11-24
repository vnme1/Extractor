#!/bin/bash

echo "========================================"
echo "  SecureDoc Extractor 시작 중..."
echo "========================================"
echo ""

# Java 버전 확인
if ! command -v java &> /dev/null; then
    echo "[오류] Java가 설치되지 않았습니다."
    echo "Java 17 이상을 설치해주세요."
    exit 1
fi

java -version

echo ""
echo "Maven 빌드 시작..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "[오류] 빌드 실패"
    exit 1
fi

echo ""
echo "========================================"
echo "  애플리케이션 실행 중..."
echo "  URL: http://localhost:18339"
echo "========================================"
echo ""

mvn spring-boot:run