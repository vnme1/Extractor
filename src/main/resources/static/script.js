// API 엔드포인트
const API_URL = '/api/extract/upload'; 

// --- DOM 요소 참조 ---
const fileInput = document.getElementById('fileInput');
const uploadButton = document.getElementById('uploadButton');
const logWindow = document.getElementById('logWindow');
const rawTextContent = document.getElementById('rawTextContent');
const documentTitle = document.getElementById('documentTitle');
const documentId = document.getElementById('documentId');
const confidenceDisplay = document.getElementById('confidenceDisplay');
const queueContainer = document.getElementById('queueContainer');

// Structured Data Input Fields
const contractorAInput = document.getElementById('contractorAInput');
const contractorBInput = document.getElementById('contractorBInput');
const startDateInput = document.getElementById('startDateInput');
const endDateInput = document.getElementById('endDateInput');
const amountInput = document.getElementById('amountInput');

// **추가된 Export 버튼 참조**
const exportButton = document.getElementById('exportButton');


// --- 이벤트 핸들러 초기화 ---
document.addEventListener('DOMContentLoaded', () => {
    // 1. 업로드 버튼 클릭 시 숨겨진 input을 클릭
    uploadButton.addEventListener('click', () => {
        fileInput.click();
    });

    // 2. 파일이 선택되었을 때 처리
    fileInput.addEventListener('change', (event) => {
        const file = event.target.files[0];
        if (file) {
            uploadFile(file);
        }
    });

    // 3. (옵션) 드래그 앤 드롭 이벤트 처리
    setupDragAndDrop();

    // **추가된 Export 버튼 클릭 이벤트**
    exportButton.addEventListener('click', exportToCSV);
});


// --- 로그 출력 함수 (이전과 동일) ---
function updateLog(message, level = 'INFO') {
    const logEntry = document.createElement('div');
    const timestamp = new Date().toLocaleTimeString();
    
    let colorClass = 'text-slate-300';
    if (level === 'INFO') {
        colorClass = 'text-green-400';
    } else if (level === 'WARN') {
        colorClass = 'text-yellow-400';
    } else if (level === 'ERROR' || level === 'FATAL') {
        colorClass = 'text-red-500';
    } else if (level === 'DEBUG') {
        colorClass = 'text-blue-400';
    }

    logEntry.className = colorClass;
    logEntry.textContent = `[${timestamp}][${level}] > ${message}`;
    logWindow.prepend(logEntry); 
}


// --- 파일 업로드 로직 (이전과 동일) ---
async function uploadFile(file) {
    
    updateLog(`File selected: ${file.name} (${(file.size / 1024 / 1024).toFixed(2)} MB)`, 'INFO');
    updateLog('Uploading file to backend API...', 'INFO');
    
    // UI 초기화
    documentTitle.textContent = file.name;
    documentId.textContent = 'ID: Processing...';
    confidenceDisplay.textContent = 'Confidence: 0%';
    rawTextContent.innerHTML = `<p>파일을 분석 중입니다. 잠시만 기다려 주세요...</p>`;
    
    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            body: formData,
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(`Server responded with status ${response.status}: ${errorData.status || 'Unknown Error'}`);
        }

        const result = await response.json();
        updateLog('File uploaded and processed successfully!', 'INFO');
        
        // 백엔드에서 받은 결과로 UI 업데이트
        updateUI(result);

    } catch (error) {
        console.error('Extraction Error:', error);
        updateLog(`File processing failed! Error: ${error.message}`, 'FATAL');
        confidenceDisplay.textContent = 'Confidence: N/A';
        documentId.textContent = 'ID: ERROR';
        // 에러 로그는 백엔드에서 받은 상세 로그를 포함하여 표시
        if (error.logs) {
            error.logs.forEach(log => updateLog(log.message, log.level));
        }
    }
}


// --- UI 업데이트 함수 (이전과 동일) ---
function updateUI(result) {
    
    // 1. 문서 정보 업데이트
    documentTitle.textContent = result.fileName || '문서명 없음';
    documentId.textContent = `ID: ${result.docId || 'N/A'}`;
    
    // 2. Raw Text 업데이트 (페이지 수 표시 추가)
    const rawTextHtml = (result.rawText || '추출된 텍스트가 없습니다.').replace(/\n/g, '<br>');
    rawTextContent.innerHTML = `<p>${rawTextHtml}</p>`;
    document.querySelector('.h3.text-sm + span').textContent = `페이지 수: ${result.totalPages || 'N/A'}`; // 페이지 수 업데이트

    // 3. Structured Data 업데이트
    contractorAInput.value = result.contractorA || '';
    contractorBInput.value = result.contractorB || '';
    startDateInput.value = result.startDate || '';
    endDateInput.value = result.endDate || '';
    
    // 금액 포맷팅 (쉼표 제거 후 다시 포맷팅하여 정확한 값 표시)
    const numericAmount = result.amount ? String(result.amount).replace(/[^0-9]/g, '') : '';
    const formattedAmount = numericAmount ? Number(numericAmount).toLocaleString('ko-KR') : '';
    amountInput.value = formattedAmount;

    // 4. Confidence 업데이트
    const confidence = (result.confidence * 100).toFixed(0) + '%';
    confidenceDisplay.textContent = `Confidence: ${confidence}`;
    confidenceDisplay.className = `text-xs px-2 py-0.5 rounded ${result.confidence >= 0.8 ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`;

    // 5. 로그 업데이트
    if (result.logs && Array.isArray(result.logs)) {
        logWindow.innerHTML = '<div class="text-white">> SecureDoc Extractor Ready.</div>'; // 로그 초기화
        result.logs.forEach(log => {
            updateLog(log.message, log.level);
        });
    }
}


// --- (옵션) 드래그 앤 드롭 구현 (이전과 동일) ---
function setupDragAndDrop() {
    // ... (로직 생략: 이전과 동일) ...
    // 파일 업로드 영역 (버튼이 있는 div)
    const dropArea = uploadButton.closest('div'); 
    
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropArea.addEventListener(eventName, preventDefaults, false);
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropArea.addEventListener(eventName, highlight, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropArea.addEventListener(eventName, unhighlight, false);
    });

    dropArea.addEventListener('drop', handleDrop, false);

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    function highlight(e) {
        uploadButton.classList.add('border-blue-500', 'bg-blue-200');
    }

    function unhighlight(e) {
        uploadButton.classList.remove('border-blue-500', 'bg-blue-200');
    }

    function handleDrop(e) {
        const dt = e.dataTransfer;
        const files = dt.files;

        if (files.length > 0 && files[0].type === 'application/pdf') {
            uploadFile(files[0]);
        } else {
            updateLog('Invalid file type dropped. Please drop a PDF file.', 'WARN');
        }
    }
}


// --- **CSV/Excel Export 로직 (새로 추가)** ---
function exportToCSV() {
    
    // 현재 UI에 표시된 데이터 수집
    const data = {
        DocID: documentId.textContent.replace('ID: ', '').trim(),
        FileName: documentTitle.textContent.trim(),
        ContractorA: contractorAInput.value.trim(),
        ContractorB: contractorBInput.value.trim(),
        StartDate: startDateInput.value.trim(),
        EndDate: endDateInput.value.trim(),
        Amount: amountInput.value.trim().replaceAll(',', ''), // 쉼표 제거
        Confidence: confidenceDisplay.textContent.replace('Confidence: ', '').trim(),
    };

    // 1. CSV 헤더 및 데이터 생성
    const headers = Object.keys(data);
    const values = Object.values(data).map(value => `"${value}"`); // 쉼표 문제 방지를 위해 따옴표로 감싸기

    let csvContent = headers.join(',') + '\n'; // 헤더 행
    csvContent += values.join(',') + '\n'; // 데이터 행
    
    // 2. 파일 다운로드 트리거
    const blob = new Blob(["\ufeff" + csvContent], { type: 'text/csv;charset=utf-8;' }); // UTF-8 BOM 추가
    const url = URL.createObjectURL(blob);
    
    const link = document.createElement('a');
    link.setAttribute('href', url);
    
    // 파일명 설정 (파일명 + timestamp)
    const baseName = data.FileName.replace(/\.pdf$/i, '') || 'extracted_data';
    link.setAttribute('download', `${baseName}_${new Date().toISOString().slice(0, 10)}.csv`); 
    
    // 숨겨진 링크 클릭하여 다운로드 시작
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    updateLog('Exported data to CSV file.', 'INFO');

}