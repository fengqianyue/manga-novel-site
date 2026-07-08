package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.Chapter;
import com.manganovel.security.RequireRole;
import com.manganovel.service.IChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chapter")
@Tag(name = "章节管理")
public class ChapterController {

    private final IChapterService chapterService;

    public ChapterController(IChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/list/{workId}")
    @Operation(summary = "查询作品的所有章节")
    public Result<List<Chapter>> list(@PathVariable Long workId) {
        return Result.ok(chapterService.listByWorkId(workId));
    }

    @GetMapping("/first/{workId}")
    @Operation(summary = "查询作品的第一章节")
    public Result<Chapter> first(@PathVariable Long workId) {
        Chapter ch = chapterService.getFirstChapter(workId);
        return ch != null ? Result.ok(ch) : Result.fail("暂无章节");
    }

    @GetMapping("/pre/{chapterId}")
    @Operation(summary = "上一章 ID")
    public Result<Long> preChapter(@PathVariable Long chapterId) {
        Long id = chapterService.getPreChapterId(chapterId);
        return id != null ? Result.ok(id) : Result.fail("已是第一章");
    }

    @GetMapping("/next/{chapterId}")
    @Operation(summary = "下一章 ID")
    public Result<Long> nextChapter(@PathVariable Long chapterId) {
        Long id = chapterService.getNextChapterId(chapterId);
        return id != null ? Result.ok(id) : Result.fail("已是最后一章");
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询章节详情")
    public Result<Chapter> detail(@PathVariable Long id) {
        Chapter chapter = chapterService.getById(id);
        if (chapter == null) {
            return Result.fail("章节不存在");
        }
        return Result.ok(chapter);
    }

    @PostMapping
    @RequireRole
    @Operation(summary = "新增章节")
    public Result<Long> add(@Valid @RequestBody Chapter chapter) {
        chapter.setId(null);
        chapterService.save(chapter);
        return Result.ok(chapter.getId());
    }

    @PutMapping
    @RequireRole
    @Operation(summary = "修改章节")
    public Result<?> update(@Valid @RequestBody Chapter chapter) {
        chapterService.updateById(chapter);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole
    @Operation(summary = "删除章节")
    public Result<?> delete(@PathVariable Long id) {
        chapterService.removeById(id);
        return Result.ok();
    }
}
