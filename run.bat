@echo off
echo ========================================
echo   SecureDoc Extractor 시작 중...
echo ========================================
echo.

REM Java 버전 확인
java -version
if errorlevel 1 (
    echo [오류] Java가 설치되지 않았습니다.
    echo Java 17 이상을 설치해주세요.
    pause
    exit /b 1
)

echo.
echo Maven 빌드 시작...
call mvn clean package -DskipTests

if errorlevel 1 (
    echo [오류] 빌드 실패
    pause
    exit /b 1
)

echo.
echo ========================================
echo   애플리케이션 실행 중...
echo   URL: http://localhost:18339
echo ========================================
echo.

call mvn spring-boot:run

pause