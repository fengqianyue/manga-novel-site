package com.manganovel.controller;

import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.manganovel.entity.Chapter;
import com.manganovel.security.RequireRole;
import com.manganovel.security.WorkOwnerChecker;
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
    private final WorkOwnerChecker ownerChecker;

    public ChapterController(IChapterService chapterService, WorkOwnerChecker ownerChecker) {
        this.chapterService = chapterService;
        this.ownerChecker = ownerChecker;
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
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "新增章节（作者须为作品属主）")
    public Result<Long> add(@RequestHeader("Authorization") String auth,
                             @Valid @RequestBody Chapter chapter) {
        if (chapter.getWorkId() == null) throw new BusinessException("缺少作品ID");
        ownerChecker.check(chapter.getWorkId(), auth);
        chapter.setId(null);
        chapterService.save(chapter);
        return Result.ok(chapter.getId());
    }

    @PutMapping
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "修改章节（作者须为作品属主）")
    public Result<?> update(@RequestHeader("Authorization") String auth,
                             @Valid @RequestBody Chapter chapter) {
        if (chapter.getId() == null) throw new BusinessException("缺少章节ID");
        Chapter existing = chapterService.getById(chapter.getId());
        if (existing == null) throw new BusinessException("章节不存在");
        ownerChecker.check(existing.getWorkId(), auth);
        chapter.setWorkId(existing.getWorkId());
        chapterService.updateById(chapter);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "删除章节（作者须为作品属主）")
    public Result<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Chapter existing = chapterService.getById(id);
        if (existing == null) throw new BusinessException("章节不存在");
        ownerChecker.check(existing.getWorkId(), auth);
        chapterService.removeById(id);
        return Result.ok();
    }
}
