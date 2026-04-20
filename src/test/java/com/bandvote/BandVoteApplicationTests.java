package com.bandvote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bandvote.model.Song;
import com.bandvote.service.VoteService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "bandvote.db.path=target/test-data/bandvote-test.db")
class BandVoteApplicationTests {

    @Autowired
    private VoteService voteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithSongListAccess() {
        assertNotNull(voteService.getSongs());
    }

    @Test
    void songCrudWorks() {
        String uniqueTitle = "테스트 곡 " + UUID.randomUUID();
        Song created = voteService.addSong(uniqueTitle, "https://youtube.com/watch?v=test123");

        assertNotNull(created.getId());
        assertEquals(uniqueTitle, created.getTitle());

        Song updated = voteService.updateSong(created.getId(), uniqueTitle + " 수정", "https://youtube.com/watch?v=updated123");
        assertEquals(uniqueTitle + " 수정", updated.getTitle());

        voteService.deleteSong(created.getId());
        assertFalse(voteService.getSongs().stream().anyMatch(song -> song.getId().equals(created.getId())));
    }

    @Test
    void excelUploadImportsSongs() throws IOException {
        String uniqueTitle = "엑셀 업로드 곡 " + UUID.randomUUID();
        byte[] excelBytes = createExcelFile(uniqueTitle, "https://youtube.com/watch?v=excel123");

        List<Song> importedSongs = voteService.importSongsFromExcel(excelBytes);

        assertEquals(1, importedSongs.size());
        assertEquals(uniqueTitle, importedSongs.get(0).getTitle());
        assertTrue(voteService.getSongs().stream().anyMatch(song -> song.getTitle().equals(uniqueTitle)));
    }

    @Test
    void voteDetailsReadsLegacyEpochTimestampWithoutFailing() {
        Song song = voteService.addSong("레거시 시간 테스트 " + UUID.randomUUID(), "https://youtube.com/watch?v=legacy123");
        long voteId = System.currentTimeMillis();
        String epochMillis = String.valueOf(System.currentTimeMillis());

        jdbcTemplate.update("INSERT INTO votes (id, voter_name, submitted_at) VALUES (?, ?, ?)", voteId, "테스터", epochMillis);
        jdbcTemplate.update("INSERT INTO vote_songs (vote_id, song_id) VALUES (?, ?)", voteId, song.getId());

        List<Map<String, Object>> details = voteService.getVoteDetails();

        assertTrue(details.stream().anyMatch(item -> item.get("id").equals(voteId)));
    }

    @Test
    void deleteVoteRemovesSubmittedData() {
        Song song = voteService.addSong("삭제 테스트 곡 " + UUID.randomUUID(), "https://youtube.com/watch?v=delete123");
        long voteId = System.currentTimeMillis();

        jdbcTemplate.update("INSERT INTO votes (id, voter_name, submitted_at) VALUES (?, ?, ?)", voteId, "삭제테스터", "2026-04-20 04:13:00.000");
        jdbcTemplate.update("INSERT INTO vote_songs (vote_id, song_id) VALUES (?, ?)", voteId, song.getId());

        voteService.deleteVote(voteId);

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM votes WHERE id = ?", Integer.class, voteId);
        assertEquals(0, count);
    }

    private byte[] createExcelFile(String title, String youtubeUrl) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("songs");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("곡명");
            header.createCell(1).setCellValue("url");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(title);
            row.createCell(1).setCellValue(youtubeUrl);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
