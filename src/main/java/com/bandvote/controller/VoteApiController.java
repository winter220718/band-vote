package com.bandvote.controller;

import com.bandvote.model.Song;
import com.bandvote.model.VoteRequest;
import com.bandvote.service.VoteService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class VoteApiController {

    private final VoteService voteService;

    public VoteApiController(VoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping("/songs")
    public Map<String, Object> getSongs() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("songs", voteService.getSongs());
        return response;
    }

    @PostMapping("/votes")
    public ResponseEntity<?> submitVote(@Valid @RequestBody VoteRequest request) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "투표가 완료되었습니다.");
            response.put("vote", voteService.submitVote(request));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/admin/dashboard")
    public Map<String, Object> getDashboard() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("songs", voteService.getSongs());
        response.put("summary", voteService.getVoteSummary());
        response.put("votes", voteService.getVoteDetails());
        return response;
    }

    @GetMapping("/admin/export")
    public ResponseEntity<byte[]> exportVoteResults() {
        byte[] file = voteService.exportVoteResultsToExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=band-vote-results.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @PostMapping("/admin/songs")
    public ResponseEntity<?> addSong(@RequestBody Map<String, String> request) {
        try {
            String title = request.getOrDefault("title", "");
            String youtubeUrl = request.getOrDefault("youtubeUrl", "");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "곡이 추가되었습니다.");
            response.put("song", voteService.addSong(title, youtubeUrl));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/admin/songs/upload")
    public ResponseEntity<?> uploadSongs(@RequestParam("file") MultipartFile file) {
        try {
            List<Song> songs = voteService.importSongsFromExcel(file.getBytes());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", songs.size() + "곡이 엑셀에서 추가되었습니다.");
            response.put("songs", songs);
            response.put("count", songs.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "엑셀 파일을 읽지 못했습니다."));
        }
    }

    @PutMapping("/admin/songs/{id}")
    public ResponseEntity<?> updateSong(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String title = request.getOrDefault("title", "");
            String youtubeUrl = request.getOrDefault("youtubeUrl", "");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "곡이 수정되었습니다.");
            response.put("song", voteService.updateSong(id, title, youtubeUrl));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/admin/songs/{id}")
    public ResponseEntity<?> deleteSong(@PathVariable Long id) {
        try {
            voteService.deleteSong(id);
            return ResponseEntity.ok(Map.of("message", "곡이 삭제되었습니다."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/admin/votes/{id}")
    public ResponseEntity<?> deleteVote(@PathVariable Long id) {
        try {
            voteService.deleteVote(id);
            return ResponseEntity.ok(Map.of("message", "제출 데이터가 삭제되었습니다."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
