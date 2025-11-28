// ===== 전역 변수 =====
let currentDocument = null;
let documentId = null;

// ===== 초기화 =====
document.addEventListener('DOMContentLoaded', function() {
    // URL에서 문서 ID 가져오기
    const urlParams = new URLSearchParams(window.location.search);
    documentId = urlParams.get('id');

    if (!documentId) {
        alert('문서 ID가 없습니다.');
        window.location.href = '/documents.html';
        return;
    }

    // 사용자 정보 표시
    const userDisplay = document.getElementById('userDisplay');
    const currentUser = sessionStorage.getItem('username');
    if (currentUser) {
        userDisplay.textContent = currentUser.substring(0, 2).toUpperCase();
        userDisplay.title = `${currentUser} (로그아웃)`;
    }

    userDisplay.addEventListener('click', () => {
        if (confirm('로그아웃 하시겠습니까?')) {
            sessionStorage.clear();
            window.location.href = '/login.html';
        }
    });

    // 문서 데이터 로드
    loadDocumentData();
});

// ===== 문서 데이터 로드 =====
async function loadDocumentData() {
    try {
        const token = sessionStorage.getItem('token');
        const response = await fetch(`/api/extract/documents/${documentId}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('문서 데이터 로드 실패');
        }

        currentDocument = await response.json();
        populateFormFields();
        loadPdfViewer();

    } catch (error) {
        console.error('문서 로드 실패:', error);
        alert('문서 데이터를 불러오는데 실패했습니다.');
        window.location.href = '/documents.html';
    }
}

// ===== 폼 필드 채우기 =====
function populateFormFields() {
    // 헤더 타이틀
    document.getElementById('docTitle').textContent = `DOC-${currentDocument.id} / ${currentDocument.filename || '문서명 없음'}`;

    // 신뢰도 배지
    const confidence = currentDocument.confidence || 0;
    const confidenceBadge = document.getElementById('confidenceBadge');
    confidenceBadge.textContent = `Confidence: ${confidence}%`;

    if (confidence >= 90) {
        confidenceBadge.className = 'px-3 py-1 text-xs font-semibold rounded-full bg-green-100 text-green-800';
    } else if (confidence >= 70) {
        confidenceBadge.className = 'px-3 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-800';
    } else if (confidence >= 50) {
        confidenceBadge.className = 'px-3 py-1 text-xs font-semibold rounded-full bg-yellow-100 text-yellow-800';
    } else {
        confidenceBadge.className = 'px-3 py-1 text-xs font-semibold rounded-full bg-red-100 text-red-800';
    }

    // 상태
    const statusDisplay = document.getElementById('statusDisplay');
    statusDisplay.innerHTML = getStatusBadge(currentDocument.status);

    // 계약자 정보
    document.getElementById('contractorA').value = currentDocument.contractorA || '';
    document.getElementById('contractorB').value = currentDocument.contractorB || '';

    // 계약 금액
    const amountInput = document.getElementById('contractAmount');
    amountInput.value = currentDocument.contractAmount || '';

    // 신뢰도가 낮으면 경고 표시
    if (confidence < 70) {
        document.getElementById('amountWarning').classList.remove('hidden');
        document.getElementById('amountContainer').classList.add('border-red-500', 'bg-red-50');
    }

    // 날짜
    document.getElementById('startDate').value = currentDocument.startDate || '';
    document.getElementById('endDate').value = currentDocument.endDate || '';

    // 업로드일
    if (currentDocument.createdAt) {
        const date = new Date(currentDocument.createdAt);
        document.getElementById('uploadDate').textContent = date.toLocaleString('ko-KR');
    }
}

// ===== PDF 뷰어 로드 =====
async function loadPdfViewer() {
    const pdfViewer = document.getElementById('pdfViewer');

    try {
        // PDF.js workerSrc 설정
        pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

        // PDF 파일 경로
        const pdfPath = `/api/extract/documents/${currentDocument.id}/pdf`;

        // PDF 로딩
        const loadingTask = pdfjsLib.getDocument({
            url: pdfPath,
            httpHeaders: {
                'Authorization': `Bearer ${sessionStorage.getItem('token')}`
            }
        });

        const pdf = await loadingTask.promise;

        // PDF 뷰어 초기화
        pdfViewer.innerHTML = '';

        // 모든 페이지 렌더링
        for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
            const page = await pdf.getPage(pageNum);
            const scale = 1.5;
            const viewport = page.getViewport({ scale: scale });

            // 캔버스 생성
            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');
            canvas.height = viewport.height;
            canvas.width = viewport.width;
            canvas.className = 'mb-4 shadow-lg';

            // 페이지 렌더링
            const renderContext = {
                canvasContext: context,
                viewport: viewport
            };

            await page.render(renderContext).promise;
            pdfViewer.appendChild(canvas);
        }

    } catch (error) {
        console.error('PDF 로딩 실패:', error);
        pdfViewer.innerHTML = `
            <div class="p-8 text-slate-400 text-center">
                <i class="fas fa-exclamation-triangle text-6xl text-red-500 mb-4"></i>
                <p class="text-lg font-medium mb-2 text-slate-700">PDF를 불러올 수 없습니다</p>
                <p class="text-sm text-slate-500">${error.message || '알 수 없는 오류'}</p>
            </div>
        `;
    }
}

// ===== 유틸리티 함수 =====
function getStatusBadge(status) {
    const badges = {
        'completed': '<span class="px-2 py-1 text-xs font-medium rounded-full bg-green-100 text-green-700">완료</span>',
        'pending': '<span class="px-2 py-1 text-xs font-medium rounded-full bg-yellow-100 text-yellow-700">대기 중</span>',
        'processing': '<span class="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-700">처리 중</span>',
        'error': '<span class="px-2 py-1 text-xs font-medium rounded-full bg-red-100 text-red-700">오류</span>'
    };
    return badges[status] || '<span class="px-2 py-1 text-xs font-medium rounded-full bg-slate-100 text-slate-700">알 수 없음</span>';
}

// ===== 재추출 요청 =====
async function requestReExtraction() {
    if (!confirm('이 문서를 재추출 하시겠습니까?')) {
        return;
    }

    try {
        const token = sessionStorage.getItem('token');
        const response = await fetch(`/api/extract/documents/${documentId}/reprocess`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('재추출 요청 실패');
        }

        alert('재추출 요청이 완료되었습니다.');
        window.location.reload();

    } catch (error) {
        console.error('재추출 요청 실패:', error);
        alert('재추출 요청에 실패했습니다.');
    }
}

// ===== 최종 검증 완료 =====
async function finalizeVerification() {
    const checkbox = document.getElementById('verifyCheckbox');
    if (!checkbox.checked) {
        alert('검증 확인 체크박스를 체크해주세요.');
        return;
    }

    // 수정된 데이터 수집
    const updatedData = {
        contractorA: document.getElementById('contractorA').value,
        contractorB: document.getElementById('contractorB').value,
        contractAmount: document.getElementById('contractAmount').value,
        startDate: document.getElementById('startDate').value,
        endDate: document.getElementById('endDate').value,
        status: 'completed'
    };

    try {
        const token = sessionStorage.getItem('token');
        const response = await fetch(`/api/extract/documents/${documentId}`, {
            method: 'PUT',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(updatedData)
        });

        if (!response.ok) {
            throw new Error('검증 완료 처리 실패');
        }

        alert('검증이 완료되었습니다.');
        window.location.href = '/documents.html';

    } catch (error) {
        console.error('검증 완료 실패:', error);
        alert('검증 완료 처리에 실패했습니다.');
    }
}
