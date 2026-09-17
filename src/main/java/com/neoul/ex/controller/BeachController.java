package com.neoul.ex.controller;

import com.neoul.ex.dto.BeachResponse;
import com.neoul.ex.service.BeachService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/beaches")
@RequiredArgsConstructor
public class BeachController {

    private final BeachService beachService;

    @GetMapping
    public List<BeachResponse> getBeaches(@RequestParam(required = false) String keyword) {
        return beachService.getBeaches(keyword);
    }
}
