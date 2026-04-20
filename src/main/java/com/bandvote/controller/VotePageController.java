package com.bandvote.controller;

import com.bandvote.service.VoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class VotePageController {

    private final VoteService voteService;

    public VotePageController(VoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("participantCount", voteService.getParticipantCount());
        return "index";
    }

    @GetMapping("/vote")
    public String votePage(@RequestParam(name = "name", required = false) String name, Model model) {
        if (name == null || name.trim().isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("voterName", name.trim());
        return "vote";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }
}
