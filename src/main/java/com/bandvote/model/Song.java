package com.bandvote.model;

public class Song {

    private Long id;
    private String title;
    private String youtubeUrl;

    public Song() {
    }

    public Song(Long id, String title, String youtubeUrl) {
        this.id = id;
        this.title = title;
        this.youtubeUrl = youtubeUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl;
    }
}
