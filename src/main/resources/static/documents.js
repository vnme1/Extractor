// ===== 전역 변수 =====
let allDocuments = [];
let filteredDocuments = [];

// ===== 초기화 =====
document.addEventListener('DOMContentLoaded', function() {
    initializePage();
    loadDocuments();

    // 사용자 정보 표시 및 로그아웃 처리
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

    // 검색 및 필터 이벤트 리스너
    const searchInput = document.getElementById('searchInput');
    const statusFilter = document.getElementById('statusFilter');

    searchInput.addEventListener('input', filterDocuments);
    statusFilter.addEventListener('change', filterDocuments);
});

function initializePage() {
    console.log('문서 목록 페이지 초기화 완료');
}

// ===== 문서 목록 로드 =====
async function loadDocuments() {
    try {
        const token = sessionStorage.getItem('token');
        const response = await fetch('/api/extract/documents', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('문서 목록 로드 실패');
        }

        allDocuments = await response.json();
        filteredDocuments = [...allDocuments];
        renderDocuments();

    } catch (error) {
        console.error('문서 로드 실패:', error);
        const tbody = document.getElementById('documentsTableBody');
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="px-6 py-12 text-center text-red-500">
                    <i class="fas fa-exclamation-circle text-2xl mb-2"></i>
                    <p>문서 목록을 불러오는데 실패했습니다.</p>
                </td>
            </tr>
        `;
    }
}

// ===== 문서 필터링 =====
function filterDocuments() {
    const searchTerm = document.getElementById('searchInput').value.toLowerCase();
    const statusFilter = document.getElementById('statusFilter').value;

    filteredDocuments = allDocuments.filter(doc => {
        // 검색어 필터
        const matchesSearch = !searchTerm ||
            (doc.filename && doc.filename.toLowerCase().includes(searchTerm)) ||
            (doc.contractorA && doc.contractorA.toLowerCase().includes(searchTerm)) ||
            (doc.contractorB && doc.contractorB.toLowerCase().includes(searchTerm)) ||
            (doc.contractAmount && doc.contractAmount.toLowerCase().includes(searchTerm));

        // 상태 필터
        const matchesStatus = !statusFilter || doc.status === statusFilter;

        return matchesSearch && matchesStatus;
    });

    renderDocuments();
}

// ===== 문서 렌더링 =====
function renderDocuments() {
    const tbody = document.getElementById('documentsTableBody');

    if (filteredDocuments.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="px-6 py-12 text-center text-slate-400">
                    <i class="fas fa-inbox text-3xl mb-2"></i>
                    <p>검색 결과가 없습니다.</p>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = filteredDocuments.map(doc => {
        const statusBadge = getStatusBadge(doc.status);
        const confidenceBadge = getConfidenceBadge(doc.confidence);
        const uploadDate = formatDate(doc.createdAt);

        return `
            <tr class="hover:bg-slate-50 transition cursor-pointer" onclick="viewDocument(${doc.id})">
                <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900">
                    #${doc.id}
                </td>
                <td class="px-6 py-4 text-sm text-slate-700">
                    <div class="flex items-center gap-2">
                        <i class="fas fa-file-pdf text-red-500"></i>
                        <span class="truncate max-w-xs">${doc.filename || 'N/A'}</span>
                    </div>
                </td>
                <td class="px-6 py-4 text-sm text-slate-700">
                    ${doc.contractorA || '-'}
                </td>
                <td class="px-6 py-4 text-sm text-slate-700">
                    ${doc.contractorB || '-'}
                </td>
                <td class="px-6 py-4 text-sm text-slate-700 font-medium">
                    ${doc.contractAmount || '-'}
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm">
                    ${confidenceBadge}
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm">
                    ${statusBadge}
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-slate-500">
                    ${uploadDate}
                </td>
            </tr>
        `;
    }).join('');
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

function getConfidenceBadge(confidence) {
    if (!confidence && confidence !== 0) return '-';

    const value = parseFloat(confidence);
    let colorClass = '';

    if (value >= 90) {
        colorClass = 'bg-green-100 text-green-700';
    } else if (value >= 70) {
        colorClass = 'bg-blue-100 text-blue-700';
    } else if (value >= 50) {
        colorClass = 'bg-yellow-100 text-yellow-700';
    } else {
        colorClass = 'bg-red-100 text-red-700';
    }

    return `<span class="px-2 py-1 text-xs font-medium rounded-full ${colorClass}">${value}%</span>`;
}

function formatDate(dateString) {
    if (!dateString) return '-';

    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// ===== 문서 상세 보기 =====
function viewDocument(documentId) {
    // 문서 상세 페이지로 이동 또는 모달 표시
    window.location.href = `/index.html?documentId=${documentId}`;
}
