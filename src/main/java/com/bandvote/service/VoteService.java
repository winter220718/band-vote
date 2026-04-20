package com.bandvote.service;

import com.bandvote.model.Song;
import com.bandvote.model.VoteEntry;
import com.bandvote.model.VoteRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.annotation.PostConstruct;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class VoteService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path storageDir = Paths.get("data");
    private final Path songsFile = storageDir.resolve("songs.json");
    private final Path votesFile = storageDir.resolve("votes.json");
    private String databaseProductName = "unknown";

    public VoteService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(storageDir);
        databaseProductName = detectDatabaseProductName();
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

    public synchronized List<Song> importSongsFromExcel(byte[] excelBytes) {
        if (excelBytes == null || excelBytes.length == 0) {
            throw new IllegalArgumentException("엑셀 파일을 선택해 주세요.");
        }

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 시트가 비어 있습니다.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<Song> importedSongs = new ArrayList<>();

            for (Row row : sheet) {
                String title = formatter.formatCellValue(row.getCell(0)).trim();
                String youtubeUrl = formatter.formatCellValue(row.getCell(1)).trim();

                if (row.getRowNum() == 0 && isHeaderRow(title, youtubeUrl)) {
                    continue;
                }

                if (title.isEmpty() && youtubeUrl.isEmpty()) {
                    continue;
                }

                if (title.isEmpty() || youtubeUrl.isEmpty()) {
                    throw new IllegalArgumentException((row.getRowNum() + 1) + "행에 곡명 또는 URL이 비어 있습니다.");
                }

                if (songExists(title, youtubeUrl)) {
                    continue;
                }

                importedSongs.add(addSong(title, youtubeUrl));
            }

            if (importedSongs.isEmpty()) {
                throw new IllegalArgumentException("추가된 곡이 없습니다. 엑셀 형식이나 중복 데이터를 확인해 주세요.");
            }

            return importedSongs;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다.", ex);
        }
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

    public synchronized void deleteVote(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("존재하지 않는 제출 데이터입니다.");
        }

        jdbcTemplate.update("DELETE FROM vote_songs WHERE vote_id = ?", id);
        int deleted = jdbcTemplate.update("DELETE FROM votes WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("존재하지 않는 제출 데이터입니다.");
        }
    }

    public synchronized byte[] exportVoteResultsToExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet summarySheet = workbook.createSheet("투표 현황");
            Row summaryHeader = summarySheet.createRow(0);
            summaryHeader.createCell(0).setCellValue("곡명");
            summaryHeader.createCell(1).setCellValue("유튜브 URL");
            summaryHeader.createCell(2).setCellValue("득표수");

            int summaryRowIndex = 1;
            for (Map<String, Object> item : getVoteSummary()) {
                Row row = summarySheet.createRow(summaryRowIndex++);
                row.createCell(0).setCellValue(String.valueOf(item.getOrDefault("title", "")));
                row.createCell(1).setCellValue(String.valueOf(item.getOrDefault("youtubeUrl", "")));
                Object voteCount = item.get("votes");
                row.createCell(2).setCellValue(voteCount instanceof Number ? ((Number) voteCount).doubleValue() : 0);
            }

            Sheet voteSheet = workbook.createSheet("제출 데이터");
            Row voteHeader = voteSheet.createRow(0);
            voteHeader.createCell(0).setCellValue("이름");
            voteHeader.createCell(1).setCellValue("제출 시간");
            voteHeader.createCell(2).setCellValue("선택 곡");

            int voteRowIndex = 1;
            for (Map<String, Object> vote : getVoteDetails()) {
                @SuppressWarnings("unchecked")
                List<Song> songs = (List<Song>) vote.getOrDefault("songs", new ArrayList<Song>());
                Row row = voteSheet.createRow(voteRowIndex++);
                row.createCell(0).setCellValue(String.valueOf(vote.getOrDefault("voterName", "")));
                row.createCell(1).setCellValue(String.valueOf(vote.getOrDefault("submittedAt", "")));
                row.createCell(2).setCellValue(songs.stream().map(Song::getTitle).collect(Collectors.joining(", ")));
            }

            for (int i = 0; i < 3; i++) {
                summarySheet.autoSizeColumn(i);
                voteSheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("투표 결과 엑셀 생성 중 오류가 발생했습니다.", ex);
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
            PreparedStatement statement = prepareInsertStatement(connection, "INSERT INTO votes (voter_name, submitted_at) VALUES (?, ?)");
            statement.setString(1, voterName);
            setSubmittedAtValue(statement, 2, submittedAt);
            return statement;
        }, keyHolder);

        long voteId = extractGeneratedId(keyHolder, "투표 저장 중 오류가 발생했습니다.");
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
                    LocalDateTime submittedAt = parseSubmittedAt(rs.getObject("submitted_at"));
                    row.put("voteId", rs.getLong("vote_id"));
                    row.put("voterName", rs.getString("voter_name"));
                    row.put("submittedAt", submittedAt == null ? null : submittedAt.toString());
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
        if (isSqlite()) {
            jdbcTemplate.execute("PRAGMA foreign_keys = ON");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS songs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "title TEXT NOT NULL, "
                    + "youtube_url TEXT NOT NULL)");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS votes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "voter_name TEXT NOT NULL, "
                    + "submitted_at TEXT NOT NULL)");
        } else {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS songs ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "title VARCHAR(255) NOT NULL, "
                    + "youtube_url TEXT NOT NULL)");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS votes ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "voter_name VARCHAR(255) NOT NULL, "
                    + "submitted_at TIMESTAMP NOT NULL)");
        }

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS vote_songs ("
                + "vote_id BIGINT NOT NULL, "
                + "song_id BIGINT NOT NULL, "
                + "PRIMARY KEY (vote_id, song_id), "
                + "FOREIGN KEY (vote_id) REFERENCES votes(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE)");
    }

    private void migrateLegacyDataIfNeeded() {
        Integer songCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM songs", Integer.class);
        if (songCount != null && songCount == 0) {
            List<Song> initialSongs = readLegacySongs();
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

        syncSequencesIfNeeded();
    }

    private Song insertSong(String title, String youtubeUrl, Long fixedId) {
        if (fixedId == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = prepareInsertStatement(connection, "INSERT INTO songs (title, youtube_url) VALUES (?, ?)");
                statement.setString(1, title);
                statement.setString(2, youtubeUrl);
                return statement;
            }, keyHolder);

            long songId = extractGeneratedId(keyHolder, "곡 저장 중 오류가 발생했습니다.");
            return new Song(songId, title, youtubeUrl);
        }

        jdbcTemplate.update("INSERT INTO songs (id, title, youtube_url) VALUES (?, ?, ?)", fixedId, title, youtubeUrl);
        return new Song(fixedId, title, youtubeUrl);
    }

    private void insertLegacyVote(VoteEntry vote) {
        LocalDateTime submittedAt = vote.getSubmittedAt() == null ? LocalDateTime.now() : vote.getSubmittedAt();
        jdbcTemplate.update(
                "INSERT INTO votes (id, voter_name, submitted_at) VALUES (?, ?, ?)",
                ps -> {
                    ps.setLong(1, vote.getId());
                    ps.setString(2, vote.getVoterName());
                    setSubmittedAtValue(ps, 3, submittedAt);
                }
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

    private String detectDatabaseProductName() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (Exception ex) {
            throw new IllegalStateException("데이터베이스 연결 확인 중 오류가 발생했습니다.", ex);
        }
    }

    private PreparedStatement prepareInsertStatement(Connection connection, String sql) throws java.sql.SQLException {
        if (isPostgres()) {
            return connection.prepareStatement(sql, new String[]{"id"});
        }
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    private long extractGeneratedId(KeyHolder keyHolder, String errorMessage) {
        try {
            Number key = keyHolder.getKey();
            if (key != null) {
                return key.longValue();
            }
        } catch (InvalidDataAccessApiUsageException ignored) {
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object id = keys.get("id");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
        }

        for (Map<String, Object> keyMap : keyHolder.getKeyList()) {
            Object id = keyMap.get("id");
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
        }

        throw new IllegalStateException(errorMessage);
    }

    private void setSubmittedAtValue(PreparedStatement statement, int parameterIndex, LocalDateTime submittedAt) throws java.sql.SQLException {
        if (isSqlite()) {
            statement.setString(parameterIndex, submittedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            return;
        }
        statement.setTimestamp(parameterIndex, Timestamp.valueOf(submittedAt));
    }

    private LocalDateTime parseSubmittedAt(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Timestamp) {
            return convertServerLocalToAppZone(((Timestamp) value).toLocalDateTime());
        }

        if (value instanceof java.util.Date) {
            return Instant.ofEpochMilli(((java.util.Date) value).getTime())
                    .atZone(APP_ZONE)
                    .toLocalDateTime();
        }

        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (raw.matches("^-?\\d+$")) {
            return Instant.ofEpochMilli(Long.parseLong(raw))
                    .atZone(APP_ZONE)
                    .toLocalDateTime();
        }

        try {
            return convertServerLocalToAppZone(Timestamp.valueOf(raw).toLocalDateTime());
        } catch (IllegalArgumentException ex) {
            try {
                return convertServerLocalToAppZone(LocalDateTime.parse(raw));
            } catch (DateTimeParseException ignored) {
                throw new IllegalStateException("투표 시간 형식을 읽는 중 오류가 발생했습니다: " + raw, ex);
            }
        }
    }

    private LocalDateTime convertServerLocalToAppZone(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(APP_ZONE)
                .toLocalDateTime();
    }

    private boolean isHeaderRow(String title, String youtubeUrl) {
        String normalizedTitle = title.toLowerCase(Locale.ROOT).replace(" ", "");
        String normalizedUrl = youtubeUrl.toLowerCase(Locale.ROOT).replace(" ", "");

        return (normalizedTitle.contains("곡명") || normalizedTitle.contains("title") || normalizedTitle.contains("song"))
                && (normalizedUrl.contains("url") || normalizedUrl.contains("link") || normalizedUrl.contains("주소") || normalizedUrl.contains("youtube"));
    }

    private boolean songExists(String title, String youtubeUrl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM songs WHERE title = ? AND youtube_url = ?",
                Integer.class,
                title.trim(),
                youtubeUrl.trim()
        );
        return count != null && count > 0;
    }

    private boolean isSqlite() {
        return databaseProductName != null && databaseProductName.toLowerCase().contains("sqlite");
    }

    private boolean isPostgres() {
        return databaseProductName != null && databaseProductName.toLowerCase().contains("postgresql");
    }

    private void syncSequencesIfNeeded() {
        if (!isPostgres()) {
            return;
        }

        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('songs', 'id'), COALESCE((SELECT MAX(id) FROM songs), 0) + 1, false)");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('votes', 'id'), COALESCE((SELECT MAX(id) FROM votes), 0) + 1, false)");
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
        return new ArrayList<>();
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
