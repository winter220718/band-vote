package com.bandvote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bandvote.model.Song;
import com.bandvote.service.VoteService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "bandvote.db.path=target/test-data/bandvote-test.db")
class BandVoteApplicationTests {

    @Autowired
    private VoteService voteService;

    @Test
    void contextLoadsWithDefaultSongs() {
        assertFalse(voteService.getSongs().isEmpty());
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
}
