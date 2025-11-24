const LOGIN_API = '/api/auth/login';
const REGISTER_API = '/api/auth/register';

// DOM Elements
const loginTab = document.getElementById('loginTab');
const registerTab = document.getElementById('registerTab');
const loginForm = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const loginMessage = document.getElementById('loginMessage');
const registerMessage = document.getElementById('registerMessage');

// 탭 전환
loginTab.addEventListener('click', () => {
    loginTab.classList.add('tab-active');
    registerTab.classList.remove('tab-active');

    loginForm.classList.remove('hidden');
    registerForm.classList.add('hidden');
    loginMessage.classList.add('hidden');
    registerMessage.classList.add('hidden');

    const subtitle = document.getElementById('auth-subtitle');
    if (subtitle) subtitle.textContent = '문서 정보 추출 시스템에 로그인하세요.';
});

registerTab.addEventListener('click', () => {
    registerTab.classList.add('tab-active');
    loginTab.classList.remove('tab-active');

    registerForm.classList.remove('hidden');
    loginForm.classList.add('hidden');
    loginMessage.classList.add('hidden');
    registerMessage.classList.add('hidden');

    const subtitle = document.getElementById('auth-subtitle');
    if (subtitle) subtitle.textContent = '새로운 계정을 생성하고 서비스를 시작하세요.';
});

// 로그인 처리
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;

    if (!username || !password) {
        showMessage(loginMessage, '모든 필드를 입력해주세요', 'error');
        return;
    }

    try {
        const response = await fetch(LOGIN_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();

        if (response.ok && data.token) {
            // JWT 토큰 저장
            localStorage.setItem('token', data.token);
            localStorage.setItem('username', data.username);
            localStorage.setItem('email', data.email);
            localStorage.setItem('role', data.role);

            showMessage(loginMessage, '로그인 성공! 리다이렉트 중...', 'success');

            setTimeout(() => {
                window.location.href = '/index.html';
            }, 500);
        } else {
            showMessage(loginMessage, data.message || '로그인에 실패했습니다', 'error');
        }
    } catch (error) {
        console.error('로그인 오류:', error);
        showMessage(loginMessage, '서버와 통신 중 오류가 발생했습니다', 'error');
    }
});

// 회원가입 처리
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = document.getElementById('registerUsername').value.trim();
    const email = document.getElementById('registerEmail').value.trim();
    const password = document.getElementById('registerPassword').value;

    if (!username || !email || !password) {
        showMessage(registerMessage, '모든 필드를 입력해주세요', 'error');
        return;
    }

    if (username.length < 3 || username.length > 50) {
        showMessage(registerMessage, '사용자명은 3-50자 사이여야 합니다', 'error');
        return;
    }

    if (password.length < 8) {
        showMessage(registerMessage, '비밀번호는 최소 8자 이상이어야 합니다', 'error');
        return;
    }

    try {
        const response = await fetch(REGISTER_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, email, password })
        });

        const data = await response.json();

        if (response.ok) {
            showMessage(registerMessage, data.message || '회원가입 완료! 로그인해주세요', 'success');

            // 폼 초기화
            registerForm.reset();

            // 2초 후 로그인 탭으로 전환
            setTimeout(() => {
                loginTab.click();
            }, 2000);
        } else {
            showMessage(registerMessage, data.message || '회원가입에 실패했습니다', 'error');
        }
    } catch (error) {
        console.error('회원가입 오류:', error);
        showMessage(registerMessage, '서버와 통신 중 오류가 발생했습니다', 'error');
    }
});

// 메시지 표시 함수
function showMessage(element, message, type) {
    element.textContent = message;
    element.classList.remove('hidden', 'text-red-600', 'text-green-600', 'text-blue-600');

    if (type === 'error') {
        element.classList.add('text-red-600');
    } else if (type === 'success') {
        element.classList.add('text-green-600');
    } else {
        element.classList.add('text-blue-600');
    }

    element.classList.remove('hidden');
}

// 페이지 로드 시 이미 로그인되어 있으면 리다이렉트
document.addEventListener('DOMContentLoaded', () => {
    const token = localStorage.getItem('token');
    if (token) {
        // 토큰 유효성 간단 확인 (만료 여부는 서버에서 체크)
        window.location.href = '/index.html';
    }
});
