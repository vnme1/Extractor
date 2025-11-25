const AUDIT_API = '/api/audit';

// JWT 토큰 가져오기
function getAuthToken() {
    return localStorage.getItem('token');
}

// 인증 헤더
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

// 관리자 권한 체크
function checkAdminRole() {
    const role = localStorage.getItem('role');
    if (role !== 'ADMIN') {
        alert('관리자만 접근할 수 있습니다.');
        window.location.href = '/index.html';
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

// 액션 타입 한글 매핑
function getActionLabel(action) {
    const labels = {
        'LOGIN': '로그인',
        'LOGOUT': '로그아웃',
        'REGISTER': '회원가입',
        'DOCUMENT_UPLOAD': '문서 업로드',
        'DOCUMENT_VIEW': '문서 조회',
        'DOCUMENT_DOWNLOAD': '문서 다운로드',
        'DOCUMENT_EDIT': '문서 수정',
        'DOCUMENT_DELETE': '문서 삭제',
        'DOCUMENT_EXPORT': '문서 내보내기',
        'UNAUTHORIZED_ACCESS': '무단 접근',
        'SETTINGS_CHANGE': '설정 변경'
    };
    return labels[action] || action;
}

// 액션 타입별 배지 색상
function getActionBadgeClass(action) {
    if (action === 'LOGIN' || action === 'REGISTER') {
        return 'bg-blue-100 text-blue-800';
    } else if (action.startsWith('DOCUMENT')) {
        return 'bg-green-100 text-green-800';
    } else if (action === 'LOGOUT') {
        return 'bg-gray-100 text-gray-800';
    } else if (action === 'UNAUTHORIZED_ACCESS') {
        return 'bg-red-100 text-red-800';
    } else {
        return 'bg-yellow-100 text-yellow-800';
    }
}

// 상태별 배지 색상
function getStatusBadgeClass(status) {
    return status === 'SUCCESS' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800';
}

// 날짜 포맷팅
function formatDateTime(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
}

// 현재 페이지 및 필터
let currentPage = 0;
let currentAction = '';
const pageSize = 20;

// 페이지 로드 전 즉시 인증 및 권한 체크 (화면이 보이기 전에 리다이렉트)
if (!getAuthToken()) {
    window.location.href = '/login.html';
} else if (localStorage.getItem('role') !== 'ADMIN') {
    alert('관리자만 접근할 수 있습니다.');
    window.location.href = '/index.html';
}

// 감사 로그 로드
async function loadAuditLogs(page = 0, action = '') {
    try {
        const params = new URLSearchParams({
            page: page,
            size: pageSize,
            sortBy: 'timestamp',
            sortDir: 'DESC'
        });

        let url = AUDIT_API;
        if (action) {
            url = `${AUDIT_API}/action/${action}`;
        }

        const response = await fetch(`${url}?${params}`, {
            headers: getAuthHeaders()
        });

        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                logout();
                return;
            }
            throw new Error('로그 조회 실패');
        }

        const data = await response.json();
        displayLogs(data.content);
        updatePagination(data);

    } catch (error) {
        console.error('로그 로드 실패:', error);
        displayError('로그를 불러오는 중 오류가 발생했습니다.');
    }
}

// 로그 테이블 표시
function displayLogs(logs) {
    const tbody = document.getElementById('logTableBody');

    if (!logs || logs.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="px-6 py-12 text-center text-slate-400">
                    <i class="fas fa-inbox text-3xl mb-2"></i>
                    <p>표시할 로그가 없습니다.</p>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = logs.map(log => `
        <tr class="hover:bg-slate-50 transition">
            <td class="px-6 py-4 whitespace-nowrap text-sm font-mono text-slate-600">
                ${formatDateTime(log.timestamp)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900">
                ${escapeHtml(log.username || 'anonymous')}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getActionBadgeClass(log.action)}">
                    ${getActionLabel(log.action)}
                </span>
                ${log.status === 'FAILED' ? `<span class="ml-2 px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusBadgeClass(log.status)}">실패</span>` : ''}
            </td>
            <td class="px-6 py-4 text-sm text-slate-700">
                ${escapeHtml(log.details || '')}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm font-mono text-slate-500">
                ${escapeHtml(log.ipAddress || '-')}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                <button onclick="showDetail(${log.id})" class="text-blue-600 hover:text-blue-900">
                    <i class="fas fa-eye"></i> 보기
                </button>
            </td>
        </tr>
    `).join('');
}

// 에러 표시
function displayError(message) {
    const tbody = document.getElementById('logTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="px-6 py-12 text-center text-red-400">
                <i class="fas fa-exclamation-circle text-3xl mb-2"></i>
                <p>${message}</p>
            </td>
        </tr>
    `;
}

// 페이지네이션 업데이트
function updatePagination(data) {
    const logInfo = document.getElementById('logInfo');
    const pagination = document.getElementById('pagination');

    const start = data.number * data.size + 1;
    const end = Math.min((data.number + 1) * data.size, data.totalElements);
    logInfo.textContent = `총 ${data.totalElements}개 로그 중 ${start}-${end} 표시`;

    let paginationHTML = '';

    // 이전 버튼
    if (data.number > 0) {
        paginationHTML += `<button onclick="loadAuditLogs(${data.number - 1}, '${currentAction}')" class="px-3 py-1 border border-slate-300 rounded text-slate-600 hover:bg-slate-50">이전</button>`;
    }

    // 페이지 번호
    const maxPages = 5;
    const startPage = Math.max(0, data.number - Math.floor(maxPages / 2));
    const endPage = Math.min(data.totalPages - 1, startPage + maxPages - 1);

    for (let i = startPage; i <= endPage; i++) {
        if (i === data.number) {
            paginationHTML += `<span class="px-3 py-1 font-bold bg-blue-500 text-white rounded">${i + 1}</span>`;
        } else {
            paginationHTML += `<button onclick="loadAuditLogs(${i}, '${currentAction}')" class="px-3 py-1 border border-slate-300 rounded text-slate-600 hover:bg-slate-50">${i + 1}</button>`;
        }
    }

    // 다음 버튼
    if (data.number < data.totalPages - 1) {
        paginationHTML += `<button onclick="loadAuditLogs(${data.number + 1}, '${currentAction}')" class="px-3 py-1 border border-slate-300 rounded text-slate-600 hover:bg-slate-50">다음</button>`;
    }

    pagination.innerHTML = paginationHTML;
}

// 상세 보기
async function showDetail(logId) {
    try {
        // 전체 로그 다시 로드해서 찾기 (간단한 구현)
        const response = await fetch(`${AUDIT_API}?page=0&size=1000`, {
            headers: getAuthHeaders()
        });

        if (!response.ok) throw new Error('로그 조회 실패');

        const data = await response.json();
        const log = data.content.find(l => l.id === logId);

        if (!log) {
            alert('로그를 찾을 수 없습니다.');
            return;
        }

        const modalContent = document.getElementById('modalContent');
        modalContent.innerHTML = `
            <div class="space-y-4">
                <div class="grid grid-cols-2 gap-4">
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">로그 ID</label>
                        <p class="text-slate-900">${log.id}</p>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">사용자</label>
                        <p class="text-slate-900">${escapeHtml(log.username || 'anonymous')}</p>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">활동 유형</label>
                        <p class="text-slate-900">${getActionLabel(log.action)}</p>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">상태</label>
                        <span class="px-2 py-1 text-xs font-semibold rounded-full ${getStatusBadgeClass(log.status)}">
                            ${log.status === 'SUCCESS' ? '성공' : '실패'}
                        </span>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">타임스탬프</label>
                        <p class="text-slate-900 font-mono text-sm">${formatDateTime(log.timestamp)}</p>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-slate-500 mb-1">IP 주소</label>
                        <p class="text-slate-900 font-mono text-sm">${escapeHtml(log.ipAddress || '-')}</p>
                    </div>
                </div>
                <div>
                    <label class="block text-sm font-medium text-slate-500 mb-1">리소스</label>
                    <p class="text-slate-900">${escapeHtml(log.resource || '-')}</p>
                </div>
                ${log.documentId ? `
                <div>
                    <label class="block text-sm font-medium text-slate-500 mb-1">문서 ID</label>
                    <p class="text-slate-900 font-mono text-sm">${escapeHtml(log.documentId)}</p>
                </div>
                ` : ''}
                <div>
                    <label class="block text-sm font-medium text-slate-500 mb-1">상세 정보</label>
                    <p class="text-slate-700 bg-slate-50 p-3 rounded border border-slate-200">${escapeHtml(log.details || '-')}</p>
                </div>
                ${log.userAgent ? `
                <div>
                    <label class="block text-sm font-medium text-slate-500 mb-1">사용자 에이전트</label>
                    <p class="text-slate-600 text-xs font-mono bg-slate-50 p-2 rounded border border-slate-200">${escapeHtml(log.userAgent)}</p>
                </div>
                ` : ''}
                ${log.errorMessage ? `
                <div>
                    <label class="block text-sm font-medium text-red-500 mb-1">에러 메시지</label>
                    <p class="text-red-700 bg-red-50 p-3 rounded border border-red-200">${escapeHtml(log.errorMessage)}</p>
                </div>
                ` : ''}
            </div>
        `;

        document.getElementById('detailModal').classList.remove('hidden');
        document.getElementById('detailModal').classList.add('flex');

    } catch (error) {
        console.error('상세 정보 로드 실패:', error);
        alert('상세 정보를 불러오는 중 오류가 발생했습니다.');
    }
}

// HTML 이스케이프
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// CSV 내보내기
async function exportToCSV() {
    try {
        const response = await fetch(`${AUDIT_API}?page=0&size=10000`, {
            headers: getAuthHeaders()
        });

        if (!response.ok) throw new Error('로그 조회 실패');

        const data = await response.json();
        const logs = data.content;

        // CSV 생성
        const headers = ['타임스탬프', '사용자', '활동유형', '상태', '리소스', '문서ID', '상세정보', 'IP주소'];
        const rows = logs.map(log => [
            formatDateTime(log.timestamp),
            log.username || '',
            getActionLabel(log.action),
            log.status || '',
            log.resource || '',
            log.documentId || '',
            log.details || '',
            log.ipAddress || ''
        ]);

        let csv = headers.join(',') + '\n';
        csv += rows.map(row => row.map(cell => `"${cell}"`).join(',')).join('\n');

        // 다운로드
        const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();

    } catch (error) {
        console.error('CSV 내보내기 실패:', error);
        alert('로그를 내보내는 중 오류가 발생했습니다.');
    }
}

// 초기화
document.addEventListener('DOMContentLoaded', () => {
    // 로그인 체크
    if (!checkAuth()) return;

    // 관리자 권한 체크
    if (!checkAdminRole()) return;

    // 사용자 정보 표시
    const username = localStorage.getItem('username');
    if (username) {
        const userDisplay = document.getElementById('userDisplay');
        userDisplay.textContent = username.substring(0, 2).toUpperCase();
        userDisplay.title = `${username} - 로그아웃`;
        userDisplay.addEventListener('click', logout);
    }

    // 이벤트 리스너
    document.getElementById('actionFilter').addEventListener('change', (e) => {
        currentAction = e.target.value;
        currentPage = 0;
        loadAuditLogs(0, currentAction);
    });

    document.getElementById('exportButton').addEventListener('click', exportToCSV);

    document.getElementById('closeModal').addEventListener('click', () => {
        document.getElementById('detailModal').classList.add('hidden');
        document.getElementById('detailModal').classList.remove('flex');
    });

    // 초기 로드
    loadAuditLogs(0, '');
});
