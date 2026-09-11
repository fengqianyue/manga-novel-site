package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.Favorite;
import com.manganovel.entity.ReadingProgress;
import com.manganovel.service.IFavoriteService;
import com.manganovel.service.IReadingProgressService;
import com.manganovel.service.IWorkTagService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 用户阅读统计
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final IReadingProgressService progressService;
    private final IFavoriteService favoriteService;
    private final IWorkTagService workTagService;

    public StatsController(IReadingProgressService progressService, IFavoriteService favoriteService, IWorkTagService workTagService) {
        this.progressService = progressService;
        this.favoriteService = favoriteService;
        this.workTagService = workTagService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户阅读统计数据")
    public Result<Map<String, Object>> getUserStats(@PathVariable Long userId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 阅读进度数（正在阅读的作品数）
        List<ReadingProgress> progresses = progressService.lambdaQuery()
            .eq(ReadingProgress::getUserId, userId).list();
        stats.put("readingCount", progresses.size());

        // 总翻页数
        int totalPages = progresses.stream().mapToInt(p -> p.getPageNum() != null ? p.getPageNum() : 0).sum();
        stats.put("totalPages", totalPages);

        // 估算阅读时长（假设平均每页30秒，换算为小时）
        double estimatedHours = Math.round(totalPages * 0.5 / 60.0 * 10) / 10.0;
        stats.put("estimatedHours", estimatedHours);

        // 收藏数
        List<Favorite> favorites = favoriteService.listByUser(userId);
        stats.put("favoriteCount", favorites.size());

        // 从阅读进度中统计类型
        Set<Long> workIds = progresses.stream().map(ReadingProgress::getWorkId).collect(Collectors.toSet());
        stats.put("totalWorks", workIds.size());

        return Result.ok(stats);
    }

    @GetMapping("/heatmap/{userId}")
    @Operation(summary = "获取用户每日阅读热力图数据（最近6个月）")
    public Result<Map<String, Integer>> getHeatmap(@PathVariable Long userId) {
        // 查询用户的阅读进度记录，提取 updatedAt 日期
        List<ReadingProgress> list = progressService.lambdaQuery()
            .eq(ReadingProgress::getUserId, userId).list();

        Map<String, Integer> dailyCount = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (ReadingProgress rp : list) {
            if (rp.getUpdatedAt() != null) {
                String date = rp.getUpdatedAt().toLocalDate().format(fmt);
                dailyCount.merge(date, 1, Integer::sum);
            }
        }

        return Result.ok(dailyCount);
    }
}
