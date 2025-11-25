const USER_API = '/api/users';

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

// HTML 이스케이프
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 페이지 로드 전 즉시 인증 및 권한 체크
if (!getAuthToken()) {
    window.location.href = '/login.html';
} else if (localStorage.getItem('role') !== 'ADMIN') {
    alert('관리자만 접근할 수 있습니다.');
    window.location.href = '/index.html';
}

// 역할 한글 매핑
function getRoleLabel(role) {
    const labels = {
        'USER': '일반 사용자',
        'MANAGER': '관리자',
        'ADMIN': '최고 관리자'
    };
    return labels[role] || role;
}

// 역할별 배지 색상
function getRoleBadgeClass(role) {
    if (role === 'ADMIN') {
        return 'bg-purple-100 text-purple-800';
    } else if (role === 'MANAGER') {
        return 'bg-yellow-100 text-yellow-800';
    } else {
        return 'bg-blue-100 text-blue-800';
    }
}

// 날짜 포맷팅
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

let selectedUserId = null;

// 사용자 목록 로드
async function loadUsers() {
    try {
        const response = await fetch(USER_API, {
            headers: getAuthHeaders()
        });

        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                logout();
                return;
            }
            throw new Error('사용자 목록 조회 실패');
        }

        const users = await response.json();
        displayUsers(users);

    } catch (error) {
        console.error('사용자 로드 실패:', error);
        displayError('사용자 목록을 불러오는 중 오류가 발생했습니다.');
    }
}

// 사용자 테이블 표시
function displayUsers(users) {
    const tbody = document.getElementById('userTableBody');
    const userCount = document.getElementById('userCount');

    userCount.textContent = users.length;

    if (!users || users.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="px-6 py-12 text-center text-slate-400">
                    <i class="fas fa-inbox text-3xl mb-2"></i>
                    <p>등록된 사용자가 없습니다.</p>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr class="hover:bg-slate-50 transition">
            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900">
                ${escapeHtml(user.username)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-slate-500">
                ${escapeHtml(user.email)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${getRoleBadgeClass(user.role)}">
                    ${getRoleLabel(user.role)}
                </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${user.enabled ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}">
                    ${user.enabled ? '활성' : '비활성'}
                </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-slate-500 font-mono">
                ${formatDate(user.createdAt)}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
                <button onclick="openRoleModal(${user.id}, '${escapeHtml(user.username)}', '${user.role}')"
                        class="text-blue-600 hover:text-blue-900 mr-3">
                    <i class="fas fa-user-shield"></i> 역할 변경
                </button>
                <button onclick="toggleUserStatus(${user.id}, ${user.enabled})"
                        class="text-${user.enabled ? 'red' : 'green'}-600 hover:text-${user.enabled ? 'red' : 'green'}-900">
                    <i class="fas fa-${user.enabled ? 'ban' : 'check'}"></i> ${user.enabled ? '비활성화' : '활성화'}
                </button>
            </td>
        </tr>
    `).join('');
}

// 에러 표시
function displayError(message) {
    const tbody = document.getElementById('userTableBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="px-6 py-12 text-center text-red-400">
                <i class="fas fa-exclamation-circle text-3xl mb-2"></i>
                <p>${message}</p>
            </td>
        </tr>
    `;
}

// 역할 변경 모달 열기
function openRoleModal(userId, username, currentRole) {
    selectedUserId = userId;
    document.getElementById('modalUsername').textContent = username;
    document.getElementById('roleSelect').value = currentRole;

    const modal = document.getElementById('roleModal');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
}

// 역할 변경 모달 닫기
function closeRoleModal() {
    const modal = document.getElementById('roleModal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
    selectedUserId = null;
}

// 역할 변경
async function changeUserRole() {
    if (!selectedUserId) return;

    const newRole = document.getElementById('roleSelect').value;

    try {
        const response = await fetch(`${USER_API}/${selectedUserId}/role`, {
            method: 'PUT',
            headers: getAuthHeaders(),
            body: JSON.stringify({ role: newRole })
        });

        if (!response.ok) {
            throw new Error('역할 변경 실패');
        }

        alert('역할이 변경되었습니다.');
        closeRoleModal();
        loadUsers();

    } catch (error) {
        console.error('역할 변경 실패:', error);
        alert('역할 변경 중 오류가 발생했습니다.');
    }
}

// 사용자 활성/비활성 토글
async function toggleUserStatus(userId, currentStatus) {
    const action = currentStatus ? '비활성화' : '활성화';
    if (!confirm(`정말로 이 사용자를 ${action}하시겠습니까?`)) {
        return;
    }

    try {
        const response = await fetch(`${USER_API}/${userId}/status`, {
            method: 'PUT',
            headers: getAuthHeaders(),
            body: JSON.stringify({ enabled: !currentStatus })
        });

        if (!response.ok) {
            throw new Error('상태 변경 실패');
        }

        alert(`사용자가 ${action}되었습니다.`);
        loadUsers();

    } catch (error) {
        console.error('상태 변경 실패:', error);
        alert('상태 변경 중 오류가 발생했습니다.');
    }
}

// 사용자 추가 모달 열기
function openAddUserModal() {
    document.getElementById('newUsername').value = '';
    document.getElementById('newEmail').value = '';
    document.getElementById('newPassword').value = '';
    document.getElementById('newUserRole').value = 'USER';

    const modal = document.getElementById('addUserModal');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
}

// 사용자 추가 모달 닫기
function closeAddUserModal() {
    const modal = document.getElementById('addUserModal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
}

// 새 사용자 추가
async function addNewUser() {
    const username = document.getElementById('newUsername').value.trim();
    const email = document.getElementById('newEmail').value.trim();
    const password = document.getElementById('newPassword').value;
    const role = document.getElementById('newUserRole').value;

    // 유효성 검증
    if (!username || !email || !password) {
        alert('모든 필드를 입력해주세요.');
        return;
    }

    if (password.length < 6) {
        alert('비밀번호는 최소 6자 이상이어야 합니다.');
        return;
    }

    try {
        const response = await fetch(USER_API, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                username: username,
                email: email,
                password: password,
                role: role
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || '사용자 추가 실패');
        }

        alert('사용자가 추가되었습니다.');
        closeAddUserModal();
        loadUsers();

    } catch (error) {
        console.error('사용자 추가 실패:', error);
        alert(error.message || '사용자 추가 중 오류가 발생했습니다.');
    }
}

// 검색 기능
function setupSearch() {
    const searchInput = document.getElementById('searchInput');
    searchInput.addEventListener('input', (e) => {
        const searchTerm = e.target.value.toLowerCase();
        const rows = document.querySelectorAll('#userTableBody tr');

        rows.forEach(row => {
            const text = row.textContent.toLowerCase();
            if (text.includes(searchTerm)) {
                row.style.display = '';
            } else {
                row.style.display = 'none';
            }
        });
    });
}

// 검색창 초기화 함수
function clearSearchInput() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.value = '';
        searchInput.setAttribute('value', '');
    }
}

// 초기화 함수
function initializePage() {
    // 로그인 체크
    if (!checkAuth()) return;

    // 관리자 권한 체크
    if (!checkAdminRole()) return;

    // 검색창 즉시 초기화
    clearSearchInput();

    // 사용자 정보 표시
    const username = localStorage.getItem('username');
    if (username) {
        const userDisplay = document.getElementById('userDisplay');
        userDisplay.textContent = username.substring(0, 2).toUpperCase();
        userDisplay.title = `${username} - 로그아웃`;
        userDisplay.addEventListener('click', logout);
    }

    // 이벤트 리스너
    document.getElementById('addUserButton').addEventListener('click', openAddUserModal);
    document.getElementById('cancelAddUser').addEventListener('click', closeAddUserModal);
    document.getElementById('confirmAddUser').addEventListener('click', addNewUser);

    document.getElementById('cancelRoleChange').addEventListener('click', closeRoleModal);
    document.getElementById('confirmRoleChange').addEventListener('click', changeUserRole);

    // 모달 외부 클릭 시 닫기
    document.getElementById('addUserModal').addEventListener('click', (e) => {
        if (e.target.id === 'addUserModal') {
            closeAddUserModal();
        }
    });

    document.getElementById('roleModal').addEventListener('click', (e) => {
        if (e.target.id === 'roleModal') {
            closeRoleModal();
        }
    });

    // 검색 설정
    setupSearch();

    // 초기 로드
    loadUsers();
}

// 초기화
document.addEventListener('DOMContentLoaded', initializePage);

// pageshow 이벤트 (뒤로가기 포함)
window.addEventListener('pageshow', (event) => {
    clearSearchInput();
});
