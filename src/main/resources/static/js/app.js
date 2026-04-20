document.addEventListener('DOMContentLoaded', () => {
    const voteForm = document.getElementById('voteForm');
    const songList = document.getElementById('songList');
    const messageBox = document.getElementById('messageBox');
    const voterName = document.getElementById('voterName')?.value || '';

    const showMessage = (message, isError = false) => {
        messageBox.textContent = message;
        messageBox.classList.remove('hidden', 'success', 'error');
        messageBox.classList.add(isError ? 'error' : 'success');
    };

    const renderSongs = (songs) => {
        if (!songs.length) {
            songList.innerHTML = '<p class="muted">등록된 곡이 없습니다.</p>';
            return;
        }

        songList.innerHTML = songs.map((song) => `
            <label class="song-item song-label">
                <input type="checkbox" name="song" value="${song.id}">
                <span>
                    <span class="song-title">${song.title}</span>
                    <a class="song-link" href="${song.youtubeUrl}" target="_blank" rel="noopener noreferrer">유튜브 바로가기</a>
                </span>
            </label>
        `).join('');
    };

    const loadSongs = async () => {
        const response = await fetch('/api/songs');
        const data = await response.json();
        renderSongs(data.songs || []);
    };

    voteForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const songIds = Array.from(document.querySelectorAll('input[name="song"]:checked'))
            .map((checkbox) => Number(checkbox.value));

        const response = await fetch('/api/votes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                voterName,
                songIds
            })
        });

        const data = await response.json();
        showMessage(data.message || '처리 중 오류가 발생했습니다.', !response.ok);

        if (response.ok) {
            voteForm.reset();
        }
    });

    loadSongs().catch(() => showMessage('곡 목록을 불러오지 못했습니다.', true));
});
