package com.neoul.ex.beach.controller;

import com.neoul.ex.beach.dto.BeachResponse;
import com.neoul.ex.beach.service.BeachService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beaches")
@RequiredArgsConstructor
public class BeachController {

    private final BeachService beachService;

    @GetMapping
    public List<BeachResponse> getBeaches(@RequestParam(required = false) String keyword) {
        return beachService.getBeaches(keyword);
    }
}
