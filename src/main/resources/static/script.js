const API_URL = '/api/extract/upload';
const DOCUMENTS_API = '/api/extract/documents/recent';

// HTML 이스케이프 함수 (XSS 방어)
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// DOM Elements - null 체크 추가
const fileInput = document.getElementById('fileInput');
const uploadButton = document.getElementById('uploadButton');
const logWindow = document.getElementById('logWindow');
const rawTextContent = document.getElementById('rawTextContent');
const documentTitle = document.getElementById('documentTitle');
const documentId = document.getElementById('documentId');
const confidenceDisplay = document.getElementById('confidenceDisplay');
const pageCount = document.getElementById('pageCount');
const recentDocuments = document.getElementById('recentDocuments');
const documentCount = document.getElementById('documentCount');

const contractorAInput = document.getElementById('contractorAInput');
const contractorBInput = document.getElementById('contractorBInput');
const startDateInput = document.getElementById('startDateInput');
const endDateInput = document.getElementById('endDateInput');
const amountInput = document.getElementById('amountInput');

const exportButton = document.getElementById('exportButton');
const verifyButton = document.getElementById('verifyButton');

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    if (uploadButton) uploadButton.addEventListener('click', () => fileInput.click());
    if (fileInput) fileInput.addEventListener('change', handleFileSelect);
    if (exportButton) exportButton.addEventListener('click', exportToCSV);
    if (verifyButton) verifyButton.addEventListener('click', verifyDocument);
    
    setupDragAndDrop();
    loadRecentDocuments();
});

// File Selection Handler
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (file) {
        uploadFile(file);
    }
    fileInput.value = '';
}

// File Upload Function
async function uploadFile(file) {
    if (!file.type.includes('pdf')) {
        addLog('ERROR', 'PDF 파일만 업로드 가능합니다');
        return;
    }

    addLog('INFO', `파일 선택: ${file.name} (${(file.size / 1024 / 1024).toFixed(2)} MB)`);
    addLog('INFO', '서버에 업로드 중...');

    showProcessing();

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.logs?.[0]?.message || '서버 오류 발생');
        }

        const result = await response.json();
        addLog('INFO', '파일 처리 완료!');
        
        updateUI(result);
        loadRecentDocuments();

    } catch (error) {
        console.error('처리 실패:', error);
        addLog('ERROR', `처리 실패: ${error.message}`);
        showError();
    }
}

// UI Update Function with null checks
function updateUI(result) {
    if (documentTitle) documentTitle.textContent = result.fileName || '문서명 없음';
    if (documentId) documentId.textContent = `ID: ${result.docId || 'N/A'}`;
    if (pageCount) pageCount.textContent = `페이지: ${result.totalPages || 'N/A'}`;

    if (rawTextContent) {
        rawTextContent.innerHTML = `<p class="whitespace-pre-wrap">${escapeHtml(result.rawText || '텍스트 없음')}</p>`;
    }

    if (contractorAInput) contractorAInput.value = result.contractorA || '';
    if (contractorBInput) contractorBInput.value = result.contractorB || '';
    if (startDateInput) startDateInput.value = result.startDate || '';
    if (endDateInput) endDateInput.value = result.endDate || '';
    
    if (amountInput) {
        // amount가 -1이면 추출 실패, 0 이상이면 유효한 값
        const formattedAmount = (result.amount >= 0) ? Number(result.amount).toLocaleString('ko-KR') : '';
        amountInput.value = formattedAmount;
    }

    const confidence = Math.round((result.confidence || 0) * 100);
    if (confidenceDisplay) {
        confidenceDisplay.textContent = `신뢰도: ${confidence}%`;
        
        if (confidence >= 80) {
            confidenceDisplay.className = 'text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded';
        } else if (confidence >= 50) {
            confidenceDisplay.className = 'text-xs bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded';
        } else {
            confidenceDisplay.className = 'text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded';
        }
    }

    if (result.logs && Array.isArray(result.logs)) {
        result.logs.forEach(log => addLog(log.level, log.message));
    }
}

// Log Function with null check
function addLog(level, message) {
    if (!logWindow) return;
    
    const logEntry = document.createElement('div');
    const timestamp = new Date().toLocaleTimeString();
    
    let colorClass = 'text-slate-300';
    if (level === 'INFO') colorClass = 'text-emerald-400';
    else if (level === 'WARN') colorClass = 'text-yellow-400';
    else if (level === 'ERROR' || level === 'FATAL') colorClass = 'text-red-400';

    logEntry.className = colorClass;
    logEntry.textContent = `[${timestamp}] ${message}`;
    
    logWindow.insertBefore(logEntry, logWindow.firstChild);
    
    while (logWindow.children.length > 50) {
        logWindow.removeChild(logWindow.lastChild);
    }
}

// Load Recent Documents
async function loadRecentDocuments() {
    if (!recentDocuments) return;
    
    try {
        const response = await fetch(DOCUMENTS_API);
        if (!response.ok) return;

        const documents = await response.json();
        if (documentCount) documentCount.textContent = `${documents.length} 건`;

        recentDocuments.innerHTML = documents.map(doc => `
            <div class="px-4 py-3 border-b border-slate-100 hover:bg-slate-50 cursor-pointer transition"
                 onclick="loadDocument('${escapeHtml(doc.docId)}')">
                <div class="text-sm font-medium text-slate-700 truncate">${escapeHtml(doc.fileName)}</div>
                <div class="text-xs text-slate-400 mt-1">
                    <span class="inline-block bg-slate-100 px-2 py-0.5 rounded mr-2">${escapeHtml(doc.status)}</span>
                    ${new Date(doc.createdAt).toLocaleString('ko-KR')}
                </div>
            </div>
        `).join('');

    } catch (error) {
        console.error('최근 문서 로드 실패:', error);
    }
}

// Load Specific Document
async function loadDocument(docId) {
    try {
        const response = await fetch(`/api/extract/documents/${docId}`);
        if (!response.ok) return;

        const doc = await response.json();
        
        const result = {
            docId: doc.docId,
            fileName: doc.fileName,
            totalPages: doc.totalPages,
            rawText: doc.rawText,
            contractorA: doc.contractorA,
            contractorB: doc.contractorB,
            startDate: doc.startDate,
            endDate: doc.endDate,
            amount: doc.amount,
            confidence: doc.confidence,
            status: doc.status,
            logs: [{ level: 'INFO', message: '저장된 문서를 불러왔습니다' }]
        };

        updateUI(result);
        addLog('INFO', `문서 로드: ${doc.fileName}`);

    } catch (error) {
        console.error('문서 로드 실패:', error);
        addLog('ERROR', '문서 로드 실패');
    }
}

// Export to CSV
function exportToCSV() {
    const data = {
        DocID: documentId ? documentId.textContent.replace('ID: ', '').trim() : 'N/A',
        FileName: documentTitle ? documentTitle.textContent.trim() : 'N/A',
        ContractorA: contractorAInput ? contractorAInput.value.trim() : '',
        ContractorB: contractorBInput ? contractorBInput.value.trim() : '',
        StartDate: startDateInput ? startDateInput.value.trim() : '',
        EndDate: endDateInput ? endDateInput.value.trim() : '',
        Amount: amountInput ? amountInput.value.trim().replaceAll(',', '') : '',
        Confidence: confidenceDisplay ? confidenceDisplay.textContent.replace('신뢰도: ', '').trim() : '0%'
    };

    const headers = Object.keys(data);
    const values = Object.values(data).map(value => `"${value}"`);

    let csvContent = '\ufeff' + headers.join(',') + '\n' + values.join(',');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    
    const link = document.createElement('a');
    link.href = url;
    link.download = `${data.FileName.replace(/\.pdf$/i, '')}_${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    
    URL.revokeObjectURL(url);
    addLog('INFO', 'CSV 파일로 내보내기 완료');
}

// Verify Document
function verifyDocument() {
    if (!verifyButton) return;
    
    addLog('INFO', '문서 검증 완료');
    verifyButton.innerHTML = '<i class="fas fa-check mr-1"></i> 검증 완료됨';
    verifyButton.disabled = true;
    verifyButton.classList.add('opacity-50', 'cursor-not-allowed');
}

// Drag and Drop
function setupDragAndDrop() {
    if (!uploadButton) return;
    
    const dropZone = uploadButton;

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
        document.body.addEventListener(eventName, preventDefaults, false);
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.add('drag-over'), false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.remove('drag-over'), false);
    });

    dropZone.addEventListener('drop', handleDrop, false);
}

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

function handleDrop(e) {
    const files = e.dataTransfer.files;
    if (files.length > 0) {
        uploadFile(files[0]);
    }
}

// UI State Functions
function showProcessing() {
    if (rawTextContent) {
        rawTextContent.innerHTML = '<div class="text-center text-slate-400"><i class="fas fa-spinner fa-spin text-2xl"></i><p class="mt-2">처리 중...</p></div>';
    }
}

function showError() {
    if (rawTextContent) {
        rawTextContent.innerHTML = '<div class="text-center text-red-400"><i class="fas fa-exclamation-triangle text-2xl"></i><p class="mt-2">처리 실패</p></div>';
    }
}