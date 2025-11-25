// ===== 전역 변수 =====
let trendChart = null;

// ===== 초기화 =====
document.addEventListener('DOMContentLoaded', function() {
    initializePage();
    loadDashboardData();

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

    // 권한에 따른 메뉴 표시
    const userRole = sessionStorage.getItem('role');
    if (userRole === 'ADMIN' || userRole === 'MANAGER') {
        const auditLogLink = document.getElementById('auditLogLink');
        const userManagementLink = document.getElementById('userManagementLink');
        if (auditLogLink) auditLogLink.classList.remove('hidden');
        if (userManagementLink && userRole === 'ADMIN') {
            userManagementLink.classList.remove('hidden');
        }
    }
});

function initializePage() {
    console.log('대시보드 초기화 완료');
}

// ===== 대시보드 데이터 로드 =====
async function loadDashboardData() {
    try {
        await Promise.all([
            loadStatistics(),
            loadMonthlyTrends(),
            loadRecentActivities()
        ]);
    } catch (error) {
        console.error('대시보드 데이터 로드 실패:', error);
    }
}

// ===== 통계 데이터 로드 =====
async function loadStatistics() {
    try {
        const response = await fetch('/api/dashboard/stats');
        if (!response.ok) throw new Error('통계 데이터 로드 실패');

        const stats = await response.json();

        // 통계 카드 업데이트
        document.getElementById('monthlyDocs').textContent = stats.monthlyDocuments || 0;
        document.getElementById('avgAccuracy').textContent = (stats.averageAccuracy || 0) + '%';
        document.getElementById('pendingDocs').textContent = stats.pendingDocuments || 0;
        document.getElementById('errorDocs').textContent = stats.errorDocuments || 0;

    } catch (error) {
        console.error('통계 로드 실패:', error);
        document.getElementById('monthlyDocs').textContent = '오류';
        document.getElementById('avgAccuracy').textContent = '오류';
        document.getElementById('pendingDocs').textContent = '오류';
        document.getElementById('errorDocs').textContent = '오류';
    }
}

// ===== 월별 추이 차트 로드 =====
async function loadMonthlyTrends() {
    try {
        const response = await fetch('/api/dashboard/monthly-trends');
        if (!response.ok) throw new Error('월별 추이 데이터 로드 실패');

        const trends = await response.json();

        // 차트 생성
        const ctx = document.getElementById('trendChart').getContext('2d');

        if (trendChart) {
            trendChart.destroy();
        }

        trendChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: trends.labels || [],
                datasets: [{
                    label: '처리된 문서 수',
                    data: trends.data || [],
                    borderColor: '#3b82f6',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: true,
                        position: 'top'
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            precision: 0
                        }
                    }
                }
            }
        });

    } catch (error) {
        console.error('월별 추이 로드 실패:', error);
        document.getElementById('trendChart').parentElement.innerHTML =
            '<div class="h-64 flex items-center justify-center text-slate-400">차트 로드 실패</div>';
    }
}

// ===== 최근 활동 로드 =====
async function loadRecentActivities() {
    try {
        const response = await fetch('/api/dashboard/recent-activities?limit=10');
        if (!response.ok) throw new Error('최근 활동 로드 실패');

        const result = await response.json();
        const activities = result.activities || [];

        const activityList = document.getElementById('activityList');

        if (activities.length === 0) {
            activityList.innerHTML = '<div class="text-center text-slate-400 py-8">활동 내역이 없습니다</div>';
            return;
        }

        activityList.innerHTML = activities.map(activity => {
            const timestamp = new Date(activity.timestamp);
            const timeStr = formatTimestamp(timestamp);
            const actionIcon = getActionIcon(activity.action);
            const statusBadge = getStatusBadge(activity.status);

            return `
                <div class="flex items-start gap-3 p-3 rounded-lg hover:bg-slate-50 transition">
                    <div class="flex-shrink-0 w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center">
                        <i class="${actionIcon} text-blue-600 text-sm"></i>
                    </div>
                    <div class="flex-1 min-w-0">
                        <p class="text-sm font-medium text-slate-800 truncate">
                            ${activity.username || 'Unknown'}
                        </p>
                        <p class="text-xs text-slate-600 truncate">
                            ${activity.details || activity.action}
                        </p>
                        <p class="text-xs text-slate-400 mt-1">${timeStr}</p>
                    </div>
                    ${statusBadge}
                </div>
            `;
        }).join('');

    } catch (error) {
        console.error('최근 활동 로드 실패:', error);
        document.getElementById('activityList').innerHTML =
            '<div class="text-center text-slate-400 py-8">활동 로드 실패</div>';
    }
}

// ===== 유틸리티 함수 =====

function formatTimestamp(date) {
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;
    if (diffHours < 24) return `${diffHours}시간 전`;
    if (diffDays < 7) return `${diffDays}일 전`;

    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function getActionIcon(action) {
    const icons = {
        'LOGIN': 'fas fa-sign-in-alt',
        'LOGOUT': 'fas fa-sign-out-alt',
        'DOCUMENT_UPLOAD': 'fas fa-upload',
        'DOCUMENT_VIEW': 'fas fa-eye',
        'DOCUMENT_DELETE': 'fas fa-trash',
        'DOCUMENT_EXPORT': 'fas fa-download',
        'USER_CREATE': 'fas fa-user-plus',
        'USER_UPDATE': 'fas fa-user-edit',
        'USER_DELETE': 'fas fa-user-minus',
        'ROLE_CHANGE': 'fas fa-user-shield'
    };
    return icons[action] || 'fas fa-circle';
}

function getStatusBadge(status) {
    if (status === 'SUCCESS') {
        return '<span class="text-xs px-2 py-1 bg-green-100 text-green-700 rounded">성공</span>';
    } else if (status === 'FAILED') {
        return '<span class="text-xs px-2 py-1 bg-red-100 text-red-700 rounded">실패</span>';
    }
    return '';
}
