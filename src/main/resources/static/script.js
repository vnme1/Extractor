const API_URL = '/api/extract/upload';
const DOCUMENTS_API = '/api/extract/documents/recent';

// JWT 토큰 가져오기
function getAuthToken() {
    return localStorage.getItem('token');
}

// 인증 헤더 추가
function getAuthHeaders() {
    const token = getAuthToken();
    const headers = {
        'Content-Type': 'application/json'
    };
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
}

// 로그인 체크
function checkAuth() {
    const token = getAuthToken();
    if (!token) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}

// 로그아웃
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
    window.location.href = '/login.html';
}

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
const exportExcelButton = document.getElementById('exportExcelButton');
const verifyButton = document.getElementById('verifyButton');
const deleteSelectedButton = document.getElementById('deleteSelectedButton');
const deleteAllButton = document.getElementById('deleteAllButton');

// 현재 문서 ID 저장
let currentDocId = null;

// 선택된 문서 ID 목록
let selectedDocIds = new Set();

// 페이지 로드 전 즉시 인증 체크 (화면이 보이기 전에 리다이렉트)
if (!getAuthToken()) {
    window.location.href = '/login.html';
}

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    // 로그인 체크
    if (!checkAuth()) return;

    // 사용자 정보 표시
    const username = localStorage.getItem('username');
    const role = localStorage.getItem('role');

    if (username) {
        const userDisplay = document.querySelector('.w-8.h-8.bg-slate-600');
        if (userDisplay) {
            userDisplay.textContent = username.substring(0, 2).toUpperCase();
            userDisplay.title = `${username} - 로그아웃`;
            userDisplay.addEventListener('click', logout);
        }
    }

    // 관리자 전용 UI 표시
    if (role === 'ADMIN') {
        const auditLogLink = document.getElementById('auditLogLink');
        if (auditLogLink) {
            auditLogLink.classList.remove('hidden');
        }
        const userManagementLink = document.getElementById('userManagementLink');
        if (userManagementLink) {
            userManagementLink.classList.remove('hidden');
        }
    }

    if (uploadButton) uploadButton.addEventListener('click', () => fileInput.click());
    if (fileInput) fileInput.addEventListener('change', handleFileSelect);
    if (exportButton) exportButton.addEventListener('click', exportToCSV);
    if (exportExcelButton) exportExcelButton.addEventListener('click', exportToExcel);
    if (verifyButton) verifyButton.addEventListener('click', verifyDocument);
    if (deleteSelectedButton) deleteSelectedButton.addEventListener('click', deleteSelectedDocuments);
    if (deleteAllButton) deleteAllButton.addEventListener('click', deleteAllDocuments);

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
        const token = getAuthToken();
        const headers = {};
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const response = await fetch(API_URL, {
            method: 'POST',
            headers: headers,
            body: formData
        });

        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                addLog('ERROR', '인증이 만료되었습니다. 다시 로그인해주세요');
                setTimeout(() => logout(), 2000);
                return;
            }
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
    // 현재 문서 ID 저장
    currentDocId = result.docId;

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
        const token = getAuthToken();
        const headers = {};
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const response = await fetch(DOCUMENTS_API, { headers });
        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                logout();
            }
            return;
        }

        const documents = await response.json();
        if (documentCount) documentCount.textContent = `${documents.length} 건`;

        recentDocuments.innerHTML = documents.map(doc => `
            <div class="flex items-center px-4 py-3 border-b border-slate-100 hover:bg-slate-50 transition group">
                <input type="checkbox"
                       class="mr-3 w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
                       onchange="toggleDocumentSelection('${escapeHtml(doc.docId)}', this.checked)"
                       ${selectedDocIds.has(doc.docId) ? 'checked' : ''}>
                <div class="flex-1 cursor-pointer" onclick="loadDocument('${escapeHtml(doc.docId)}')">
                    <div class="text-sm font-medium text-slate-700 truncate">${escapeHtml(doc.fileName)}</div>
                    <div class="text-xs text-slate-400 mt-1">
                        <span class="inline-block bg-slate-100 px-2 py-0.5 rounded mr-2">${escapeHtml(doc.status)}</span>
                        ${new Date(doc.createdAt).toLocaleString('ko-KR')}
                    </div>
                </div>
                <button onclick="deleteSingleDocument('${escapeHtml(doc.docId)}', event)"
                        class="ml-2 opacity-0 group-hover:opacity-100 text-red-500 hover:text-red-700 px-2 py-1 rounded transition">
                    <i class="fas fa-trash text-sm"></i>
                </button>
            </div>
        `).join('');

        updateDeleteButtonsState();

    } catch (error) {
        console.error('최근 문서 로드 실패:', error);
    }
}

// Load Specific Document
async function loadDocument(docId) {
    try {
        const token = getAuthToken();
        const headers = {};
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const response = await fetch(`/api/extract/documents/${docId}`, { headers });
        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                logout();
            }
            return;
        }

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

// Export to Excel
function exportToExcel() {
    if (!currentDocId) {
        addLog('WARN', '내보낼 문서가 없습니다');
        return;
    }

    addLog('INFO', 'Excel 파일로 내보내는 중...');

    // Excel 다운로드 API 호출
    window.location.href = `/api/extract/documents/${currentDocId}/export/excel`;

    addLog('INFO', 'Excel 파일 다운로드 시작');
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

// Document Selection Toggle
function toggleDocumentSelection(docId, checked) {
    if (checked) {
        selectedDocIds.add(docId);
    } else {
        selectedDocIds.delete(docId);
    }
    updateDeleteButtonsState();
}

// Update Delete Buttons State
function updateDeleteButtonsState() {
    if (deleteSelectedButton) {
        if (selectedDocIds.size > 0) {
            deleteSelectedButton.disabled = false;
            deleteSelectedButton.classList.remove('opacity-50', 'cursor-not-allowed');
            deleteSelectedButton.innerHTML = `<i class="fas fa-trash mr-1"></i> 선택 삭제 (${selectedDocIds.size})`;
        } else {
            deleteSelectedButton.disabled = true;
            deleteSelectedButton.classList.add('opacity-50', 'cursor-not-allowed');
            deleteSelectedButton.innerHTML = '<i class="fas fa-trash mr-1"></i> 선택 삭제';
        }
    }
}

// Delete Single Document
async function deleteSingleDocument(docId, event) {
    event.stopPropagation();

    if (!confirm('이 문서를 삭제하시겠습니까?')) {
        return;
    }

    try {
        const token = getAuthToken();
        const response = await fetch(`/api/extract/documents/${docId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            addLog('INFO', '문서가 삭제되었습니다');
            selectedDocIds.delete(docId);
            loadRecentDocuments();
        } else {
            addLog('ERROR', '문서 삭제 실패');
        }
    } catch (error) {
        console.error('문서 삭제 실패:', error);
        addLog('ERROR', '문서 삭제 중 오류 발생');
    }
}

// Delete Selected Documents
async function deleteSelectedDocuments() {
    if (selectedDocIds.size === 0) {
        return;
    }

    if (!confirm(`선택한 ${selectedDocIds.size}개 문서를 삭제하시겠습니까?`)) {
        return;
    }

    try {
        const token = getAuthToken();
        const response = await fetch('/api/extract/documents', {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(Array.from(selectedDocIds))
        });

        if (response.ok) {
            const result = await response.json();
            addLog('INFO', result.message || '선택한 문서가 삭제되었습니다');
            selectedDocIds.clear();
            loadRecentDocuments();
        } else {
            addLog('ERROR', '문서 삭제 실패');
        }
    } catch (error) {
        console.error('문서 삭제 실패:', error);
        addLog('ERROR', '문서 삭제 중 오류 발생');
    }
}

// Delete All Documents
async function deleteAllDocuments() {
    if (!confirm('⚠️ 모든 문서를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다!')) {
        return;
    }

    if (!confirm('정말로 전체 문서를 삭제하시겠습니까?')) {
        return;
    }

    try {
        const token = getAuthToken();
        const response = await fetch('/api/extract/documents/all', {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const result = await response.json();
            addLog('INFO', result.message || '전체 문서가 삭제되었습니다');
            selectedDocIds.clear();
            loadRecentDocuments();
        } else {
            addLog('ERROR', '전체 삭제 실패');
        }
    } catch (error) {
        console.error('전체 삭제 실패:', error);
        addLog('ERROR', '전체 삭제 중 오류 발생');
    }
}