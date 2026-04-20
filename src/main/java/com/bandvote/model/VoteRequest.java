package com.bandvote.model;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

public class VoteRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String voterName;

    @NotEmpty(message = "곡을 선택해 주세요.")
    private List<Long> songIds;

    public String getVoterName() {
        return voterName;
    }

    public void setVoterName(String voterName) {
        this.voterName = voterName;
    }

    public List<Long> getSongIds() {
        return songIds;
    }

    public void setSongIds(List<Long> songIds) {
        this.songIds = songIds;
    }
}
