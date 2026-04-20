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

    const escapeHtml = (value = '') => value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    const getYoutubeEmbedUrl = (url = '') => {
        try {
            const parsedUrl = new URL(url);
            const videoId = parsedUrl.hostname.includes('youtu.be')
                ? parsedUrl.pathname.slice(1)
                : parsedUrl.searchParams.get('v');

            return videoId ? `https://www.youtube.com/embed/${videoId}` : '';
        } catch (error) {
            return '';
        }
    };

    const renderSongs = (songs) => {
        if (!songs.length) {
            songList.innerHTML = '<p class="muted">등록된 곡이 없습니다.</p>';
            return;
        }

        songList.innerHTML = songs.map((song) => {
            const embedUrl = getYoutubeEmbedUrl(song.youtubeUrl);

            return `
                <label class="song-item song-label song-card">
                    <input type="checkbox" name="song" value="${song.id}">
                    <span class="song-content">
                        <span class="song-title">${escapeHtml(song.title)}</span>
                        <a class="song-link" href="${escapeHtml(song.youtubeUrl)}" target="_blank" rel="noopener noreferrer">유튜브 바로가기</a>
                        ${embedUrl ? `
                            <span class="song-embed-wrap">
                                <iframe
                                    class="song-embed"
                                    src="${embedUrl}"
                                    title="${escapeHtml(song.title)} 미리보기"
                                    loading="lazy"
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                    allowfullscreen>
                                </iframe>
                            </span>
                        ` : ''}
                    </span>
                </label>
            `;
        }).join('');
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
