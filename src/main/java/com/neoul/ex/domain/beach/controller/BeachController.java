package com.neoul.ex.domain.beach.controller;

import com.neoul.ex.domain.beach.dto.BeachDetailResponse;
import com.neoul.ex.domain.beach.dto.BeachResponse;
import com.neoul.ex.domain.beach.service.BeachService;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "해수욕장")
@RequestMapping("/beaches")
@RequiredArgsConstructor
public class BeachController {
    private final BeachService beachService;

    @GetMapping
    @Operation(summary = "해수욕장 목록 조회")
    public ResponseEntity<Message<List<BeachResponse>>> getBeaches(@RequestParam(required = false) String keyword) {
        return ResponseEntity.status(SuccessCode.BEACH_LIST_RETRIEVED.getStatus())
                .body(Message.success(SuccessCode.BEACH_LIST_RETRIEVED, beachService.getBeaches(keyword)));
    }

    @GetMapping("/{beachId}")
    @Operation(summary = "해수욕장 상세 조회")
    public ResponseEntity<Message<BeachDetailResponse>> getBeach(@PathVariable Long beachId) {
        return ResponseEntity.status(SuccessCode.BEACH_RETRIEVED.getStatus())
                .body(Message.success(SuccessCode.BEACH_RETRIEVED, beachService.getBeach(beachId)));
    }

}
