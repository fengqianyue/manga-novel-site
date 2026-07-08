package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.ReadingProgress;
import com.manganovel.security.JwtUtil;
import com.manganovel.service.IReadingProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reading-progress")
@Tag(name = "阅读进度管理")
public class ReadingProgressController {

    private final IReadingProgressService progressService;
    private final JwtUtil jwtUtil;

    public ReadingProgressController(IReadingProgressService progressService, JwtUtil jwtUtil) {
        this.progressService = progressService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/save")
    @Operation(summary = "保存阅读进度")
    public Result<?> save(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> body) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        Long workId = Long.valueOf(body.get("workId").toString());
        Long chapterId = Long.valueOf(body.get("chapterId").toString());
        Integer pageNum = Integer.valueOf(body.get("pageNum").toString());
        progressService.save(userId, workId, chapterId, pageNum);
        return Result.ok();
    }

    @GetMapping("/{workId}")
    @Operation(summary = "查询某作品的阅读进度")
    public Result<ReadingProgress> get(@RequestHeader("Authorization") String auth, @PathVariable Long workId) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        return Result.ok(progressService.getByUserAndWork(userId, workId));
    }
}
