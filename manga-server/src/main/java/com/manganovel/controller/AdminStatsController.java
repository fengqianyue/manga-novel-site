package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.User;
import com.manganovel.entity.Work;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.Comment;
import com.manganovel.entity.Tag;
import com.manganovel.service.*;
import com.manganovel.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台数据大屏统计
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final IWorkService workService;
    private final IUserService userService;
    private final IChapterService chapterService;
    private final ICommentService commentService;
    private final ITagService tagService;
    private final IWorkTagService workTagService;

    public AdminStatsController(IWorkService workService, IUserService userService,
                                IChapterService chapterService, ICommentService commentService,
                                ITagService tagService, IWorkTagService workTagService) {
        this.workService = workService;
        this.userService = userService;
        this.chapterService = chapterService;
        this.commentService = commentService;
        this.tagService = tagService;
        this.workTagService = workTagService;
    }

    @RequireRole
    @GetMapping("/dashboard")
    @Operation(summary = "管理后台数据大屏统计")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 总量统计
        long totalUsers = userService.count();
        long totalWorks = workService.count();
        long activeWorks = workService.lambdaQuery().eq(Work::getStatus, 1).isNull(Work::getUserId).count();
        long totalChapters = chapterService.count();
        long totalComments = commentService.count();

        Map<String, Long> overview = new LinkedHashMap<>();
        overview.put("totalUsers", totalUsers);
        overview.put("totalWorks", totalWorks);
        overview.put("activeWorks", activeWorks);
        overview.put("totalChapters", totalChapters);
        overview.put("totalComments", totalComments);
        data.put("overview", overview);

        // 作品类型分布（漫画 vs 小说）
        long mangaCount = workService.lambdaQuery().eq(Work::getType, "manga").count();
        long novelCount = workService.lambdaQuery().eq(Work::getType, "novel").count();
        Map<String, Long> typeDist = new LinkedHashMap<>();
        typeDist.put("manga", mangaCount);
        typeDist.put("novel", novelCount);
        data.put("typeDistribution", typeDist);

        // 热门标签 Top 10（按关联作品数排序）
        // 用 Map 一次性统计所有标签，避免 N+1
        Map<Long, Long> tagCounts = new HashMap<>();
        List<Map<String, Object>> allWorkTags = workTagService.listMaps(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.manganovel.entity.WorkTag>()
                .select("tag_id, count(*) as cnt")
                .inSql("work_id", "SELECT id FROM work WHERE status = 1 AND user_id IS NULL")
                .groupBy("tag_id")
                .orderByDesc("cnt")
                .last("LIMIT 10")
        );
        List<Map<String, Object>> hotTags = new ArrayList<>();
        for (Map<String, Object> row : allWorkTags) {
            Long tagId = Long.valueOf(row.get("tag_id").toString());
            Tag tag = tagService.getById(tagId);
            if (tag != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", tag.getName());
                item.put("value", row.get("cnt"));
                hotTags.add(item);
            }
        }

        // 近7天新增作品趋势（模拟数据，实际可查询 created_at）
        List<Map<String, Object>> recentTrend = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd");
        for (int i = 6; i >= 0; i--) {
            String dateStr = today.minusDays(i).format(fmt);
            Map<String, Object> item = new HashMap<>();
            item.put("date", dateStr);
            // 查询当天新增数量
            long count = workService.lambdaQuery()
                .ge(Work::getCreatedAt, today.minusDays(i).atStartOfDay())
                .lt(Work::getCreatedAt, today.minusDays(i - 1).atStartOfDay())
                .count();
            item.put("count", count);
            recentTrend.add(item);
        }
        data.put("recentTrend", recentTrend);

        return Result.ok(data);
    }
}
