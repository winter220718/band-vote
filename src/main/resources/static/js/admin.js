document.addEventListener('DOMContentLoaded', () => {
    const songForm = document.getElementById('songForm');
    const songFormTitle = document.getElementById('songFormTitle');
    const submitSongButton = document.getElementById('submitSongButton');
    const cancelEditButton = document.getElementById('cancelEditButton');
    const titleInput = document.getElementById('title');
    const youtubeUrlInput = document.getElementById('youtubeUrl');
    const excelUploadForm = document.getElementById('excelUploadForm');
    const excelFileInput = document.getElementById('excelFile');
    const songAdminList = document.getElementById('songAdminList');
    const summaryCards = document.getElementById('summaryCards');
    const voteDataList = document.getElementById('voteDataList');
    const messageBox = document.getElementById('adminMessageBox');
    let editingSongId = null;

    const showMessage = (message, isError = false) => {
        messageBox.textContent = message;
        messageBox.classList.remove('hidden', 'success', 'error');
        messageBox.classList.add(isError ? 'error' : 'success');
    };

    const escapeHtml = (value = '') => value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    const formatDate = (value) => {
        if (!value) {
            return '-';
        }

        const normalized = String(value).replace(' ', 'T');
        const hasZone = /[zZ]|[+-]\d{2}:\d{2}$/.test(normalized);
        const date = new Date(hasZone ? normalized : `${normalized}+09:00`);

        return Number.isNaN(date.getTime())
            ? value
            : `${date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false })} KST`;
    };

    const resetForm = () => {
        editingSongId = null;
        songForm.reset();
        songFormTitle.textContent = '곡 추가';
        submitSongButton.textContent = '곡 추가';
        cancelEditButton.classList.add('hidden');
    };

    const renderDashboard = (data) => {
        const songs = data.songs || [];
        const summary = data.summary || [];
        const votes = data.votes || [];

        songAdminList.innerHTML = songs.length
            ? songs.map((song) => `
                <li class="song-admin-row">
                    <div class="song-admin-meta">
                        <strong>${escapeHtml(song.title)}</strong>
                        <span class="muted">${escapeHtml(song.youtubeUrl)}</span>
                    </div>
                    <div class="inline-actions">
                        <button type="button" class="secondary-btn edit-song-btn" data-id="${song.id}" data-title="${escapeHtml(song.title)}" data-url="${escapeHtml(song.youtubeUrl)}">수정</button>
                        <button type="button" class="secondary-btn danger-btn delete-song-btn" data-id="${song.id}">삭제</button>
                    </div>
                </li>
            `).join('')
            : '<li class="muted">등록된 곡이 없습니다.</li>';

        summaryCards.innerHTML = summary.length
            ? summary.map((item) => `
                <article class="summary-card">
                    <div>${escapeHtml(item.title)}</div>
                    <strong>${item.votes}표</strong>
                </article>
            `).join('')
            : '<p class="muted">아직 투표가 없습니다.</p>';

        voteDataList.innerHTML = votes.length
            ? votes.map((vote) => `
                <article class="vote-item">
                    <div class="vote-item-head">
                        <h3>${escapeHtml(vote.voterName)}</h3>
                        <button type="button" class="secondary-btn danger-btn delete-vote-btn" data-id="${vote.id}">제출 삭제</button>
                    </div>
                    <p class="muted">제출 시간: ${formatDate(vote.submittedAt)}</p>
                    <p>${(vote.songs || []).map((song) => escapeHtml(song.title)).join(', ') || '선택 없음'}</p>
                </article>
            `).join('')
            : '<p class="muted">아직 제출된 데이터가 없습니다.</p>';
    };

    const loadDashboard = async () => {
        const response = await fetch('/api/admin/dashboard');
        const data = await response.json();
        renderDashboard(data);
    };

    songForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const payload = {
            title: titleInput.value.trim(),
            youtubeUrl: youtubeUrlInput.value.trim()
        };

        const isEditing = editingSongId !== null;
        const response = await fetch(isEditing ? `/api/admin/songs/${editingSongId}` : '/api/admin/songs', {
            method: isEditing ? 'PUT' : 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        const data = await response.json();
        showMessage(data.message || '처리 중 오류가 발생했습니다.', !response.ok);

        if (response.ok) {
            resetForm();
            await loadDashboard();
        }
    });

    cancelEditButton.addEventListener('click', () => {
        resetForm();
        showMessage('수정 모드가 취소되었습니다.');
    });

    excelUploadForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        if (!excelFileInput.files.length) {
            showMessage('업로드할 엑셀 파일을 선택해 주세요.', true);
            return;
        }

        const formData = new FormData();
        formData.append('file', excelFileInput.files[0]);

        const response = await fetch('/api/admin/songs/upload', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();
        showMessage(data.message || '처리 중 오류가 발생했습니다.', !response.ok);

        if (response.ok) {
            excelUploadForm.reset();
            await loadDashboard();
        }
    });

    songAdminList.addEventListener('click', async (event) => {
        const editButton = event.target.closest('.edit-song-btn');
        const deleteButton = event.target.closest('.delete-song-btn');

        if (editButton) {
            editingSongId = Number(editButton.dataset.id);
            titleInput.value = editButton.dataset.title || '';
            youtubeUrlInput.value = editButton.dataset.url || '';
            songFormTitle.textContent = '곡 수정';
            submitSongButton.textContent = '곡 수정 저장';
            cancelEditButton.classList.remove('hidden');
            titleInput.focus();
            return;
        }

        if (deleteButton) {
            const songId = Number(deleteButton.dataset.id);

            const response = await fetch(`/api/admin/songs/${songId}`, {
                method: 'DELETE'
            });
            const data = await response.json();
            showMessage(data.message || '처리 중 오류가 발생했습니다.', !response.ok);

            if (response.ok) {
                if (editingSongId === songId) {
                    resetForm();
                }
                await loadDashboard();
            }
        }
    });

    voteDataList.addEventListener('click', async (event) => {
        const deleteVoteButton = event.target.closest('.delete-vote-btn');
        if (!deleteVoteButton) {
            return;
        }

        const voteId = Number(deleteVoteButton.dataset.id);
        const response = await fetch(`/api/admin/votes/${voteId}`, {
            method: 'DELETE'
        });
        const data = await response.json();
        showMessage(data.message || '처리 중 오류가 발생했습니다.', !response.ok);

        if (response.ok) {
            await loadDashboard();
        }
    });

    resetForm();
    loadDashboard().catch(() => showMessage('관리자 데이터를 불러오지 못했습니다.', true));
});
