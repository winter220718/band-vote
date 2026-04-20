package com.bandvote.service;

import com.bandvote.model.Song;
import com.bandvote.model.VoteEntry;
import com.bandvote.model.VoteRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class VoteService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path storageDir = Paths.get("data");
    private final Path songsFile = storageDir.resolve("songs.json");
    private final Path votesFile = storageDir.resolve("votes.json");

    public VoteService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(storageDir);
        createTables();
        migrateLegacyDataIfNeeded();
    }

    public synchronized List<Song> getSongs() {
        return jdbcTemplate.query(
                "SELECT id, title, youtube_url FROM songs ORDER BY id ASC",
                (rs, rowNum) -> new Song(rs.getLong("id"), rs.getString("title"), rs.getString("youtube_url"))
        );
    }

    public synchronized Song addSong(String title, String youtubeUrl) {
        String cleanTitle = validateTitle(title);
        String cleanUrl = validateYoutubeUrl(youtubeUrl);
        return insertSong(cleanTitle, cleanUrl, null);
    }

    public synchronized Song updateSong(Long id, String title, String youtubeUrl) {
        if (id == null) {
            throw new IllegalArgumentException("존재하지 않는 곡입니다.");
        }

        String cleanTitle = validateTitle(title);
        String cleanUrl = validateYoutubeUrl(youtubeUrl);

        int updated = jdbcTemplate.update(
                "UPDATE songs SET title = ?, youtube_url = ? WHERE id = ?",
                cleanTitle, cleanUrl, id
        );

        if (updated == 0) {
            throw new IllegalArgumentException("존재하지 않는 곡입니다.");
        }

        return findSongById(id);
    }

    public synchronized void deleteSong(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("존재하지 않는 곡입니다.");
        }

        int deleted = jdbcTemplate.update("DELETE FROM songs WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("존재하지 않는 곡입니다.");
        }
    }

    public synchronized VoteEntry submitVote(VoteRequest request) {
        String voterName = request.getVoterName() == null ? "" : request.getVoterName().trim();
        if (voterName.isEmpty()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }
        if (request.getSongIds() == null || request.getSongIds().isEmpty()) {
            throw new IllegalArgumentException("최소 1곡 이상 선택해 주세요.");
        }

        Set<Long> validSongIds = getSongs().stream().map(Song::getId).collect(Collectors.toSet());
        List<Long> selectedSongIds = request.getSongIds().stream()
                .filter(validSongIds::contains)
                .distinct()
                .collect(Collectors.toList());

        if (selectedSongIds.isEmpty()) {
            throw new IllegalArgumentException("유효한 곡을 선택해 주세요.");
        }

        LocalDateTime submittedAt = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO votes (voter_name, submitted_at) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, voterName);
            statement.setString(2, submittedAt.toString());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("투표 저장 중 오류가 발생했습니다.");
        }

        long voteId = key.longValue();
        for (Long songId : selectedSongIds) {
            jdbcTemplate.update("INSERT INTO vote_songs (vote_id, song_id) VALUES (?, ?)", voteId, songId);
        }

        VoteEntry entry = new VoteEntry();
        entry.setId(voteId);
        entry.setVoterName(voterName);
        entry.setSongIds(selectedSongIds);
        entry.setSubmittedAt(submittedAt);
        return entry;
    }

    public synchronized List<Map<String, Object>> getVoteSummary() {
        return jdbcTemplate.query(
                "SELECT s.id, s.title, s.youtube_url, COUNT(vs.vote_id) AS votes "
                        + "FROM songs s "
                        + "LEFT JOIN vote_songs vs ON s.id = vs.song_id "
                        + "GROUP BY s.id, s.title, s.youtube_url "
                        + "ORDER BY votes DESC, s.id ASC",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("title", rs.getString("title"));
                    item.put("youtubeUrl", rs.getString("youtube_url"));
                    item.put("votes", rs.getInt("votes"));
                    return item;
                }
        );
    }

    public synchronized List<Map<String, Object>> getVoteDetails() {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT v.id AS vote_id, v.voter_name, v.submitted_at, "
                        + "s.id AS song_id, s.title AS song_title, s.youtube_url AS song_youtube_url "
                        + "FROM votes v "
                        + "LEFT JOIN vote_songs vs ON v.id = vs.vote_id "
                        + "LEFT JOIN songs s ON s.id = vs.song_id "
                        + "ORDER BY v.submitted_at DESC, v.id DESC, s.id ASC",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("voteId", rs.getLong("vote_id"));
                    row.put("voterName", rs.getString("voter_name"));
                    row.put("submittedAt", rs.getString("submitted_at"));
                    row.put("songId", rs.getObject("song_id") == null ? null : rs.getLong("song_id"));
                    row.put("songTitle", rs.getString("song_title"));
                    row.put("songYoutubeUrl", rs.getString("song_youtube_url"));
                    return row;
                }
        );

        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long voteId = (Long) row.get("voteId");
            Map<String, Object> vote = grouped.computeIfAbsent(voteId, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", key);
                item.put("voterName", row.get("voterName"));
                item.put("submittedAt", row.get("submittedAt"));
                item.put("songs", new ArrayList<Song>());
                return item;
            });

            if (row.get("songId") != null) {
                @SuppressWarnings("unchecked")
                List<Song> songs = (List<Song>) vote.get("songs");
                songs.add(new Song(
                        (Long) row.get("songId"),
                        (String) row.get("songTitle"),
                        (String) row.get("songYoutubeUrl")
                ));
            }
        }

        return new ArrayList<>(grouped.values());
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS songs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "youtube_url TEXT NOT NULL)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS votes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "voter_name TEXT NOT NULL, "
                + "submitted_at TEXT NOT NULL)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS vote_songs ("
                + "vote_id INTEGER NOT NULL, "
                + "song_id INTEGER NOT NULL, "
                + "PRIMARY KEY (vote_id, song_id), "
                + "FOREIGN KEY (vote_id) REFERENCES votes(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE)");
    }

    private void migrateLegacyDataIfNeeded() {
        Integer songCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM songs", Integer.class);
        if (songCount != null && songCount == 0) {
            List<Song> initialSongs = readLegacySongs();
            if (initialSongs.isEmpty()) {
                initialSongs = defaultSongs();
            }
            for (Song song : initialSongs) {
                insertSong(song.getTitle(), song.getYoutubeUrl(), song.getId());
            }
        }

        Integer voteCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM votes", Integer.class);
        if (voteCount != null && voteCount == 0) {
            for (VoteEntry vote : readLegacyVotes()) {
                insertLegacyVote(vote);
            }
        }
    }

    private Song insertSong(String title, String youtubeUrl, Long fixedId) {
        if (fixedId == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO songs (title, youtube_url) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                statement.setString(1, title);
                statement.setString(2, youtubeUrl);
                return statement;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("곡 저장 중 오류가 발생했습니다.");
            }
            return new Song(key.longValue(), title, youtubeUrl);
        }

        jdbcTemplate.update("INSERT INTO songs (id, title, youtube_url) VALUES (?, ?, ?)", fixedId, title, youtubeUrl);
        return new Song(fixedId, title, youtubeUrl);
    }

    private void insertLegacyVote(VoteEntry vote) {
        LocalDateTime submittedAt = vote.getSubmittedAt() == null ? LocalDateTime.now() : vote.getSubmittedAt();
        jdbcTemplate.update(
                "INSERT INTO votes (id, voter_name, submitted_at) VALUES (?, ?, ?)",
                vote.getId(),
                vote.getVoterName(),
                submittedAt.toString()
        );

        if (vote.getSongIds() != null) {
            for (Long songId : vote.getSongIds()) {
                jdbcTemplate.update("INSERT INTO vote_songs (vote_id, song_id) VALUES (?, ?)", vote.getId(), songId);
            }
        }
    }

    private Song findSongById(Long id) {
        List<Song> songs = jdbcTemplate.query(
                "SELECT id, title, youtube_url FROM songs WHERE id = ?",
                (rs, rowNum) -> new Song(rs.getLong("id"), rs.getString("title"), rs.getString("youtube_url")),
                id
        );
        if (songs.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 곡입니다.");
        }
        return songs.get(0);
    }

    private String validateTitle(String title) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("곡명을 입력해 주세요.");
        }
        return cleanTitle;
    }

    private String validateYoutubeUrl(String youtubeUrl) {
        String cleanUrl = youtubeUrl == null ? "" : youtubeUrl.trim();
        if (cleanUrl.isEmpty()) {
            throw new IllegalArgumentException("유튜브 링크를 입력해 주세요.");
        }
        return cleanUrl;
    }

    private List<Song> defaultSongs() {
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(1L, "한 페이지가 될 수 있게", "https://www.youtube.com/watch?v=B9FzVhw8_bY"));
        songs.add(new Song(2L, "질풍가도", "https://www.youtube.com/watch?v=7V8G6A9u1X8"));
        songs.add(new Song(3L, "좋은 밤 좋은 꿈", "https://www.youtube.com/watch?v=ebslN7g4mqA"));
        songs.add(new Song(4L, "Supernova", "https://www.youtube.com/watch?v=phuiiNCxRMg"));
        return songs;
    }

    private List<Song> readLegacySongs() {
        try {
            if (!Files.exists(songsFile)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(songsFile.toFile(), new TypeReference<List<Song>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("기존 곡 데이터를 읽는 중 오류가 발생했습니다.", ex);
        }
    }

    private List<VoteEntry> readLegacyVotes() {
        try {
            if (!Files.exists(votesFile)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(votesFile.toFile(), new TypeReference<List<VoteEntry>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("기존 투표 데이터를 읽는 중 오류가 발생했습니다.", ex);
        }
    }
}
