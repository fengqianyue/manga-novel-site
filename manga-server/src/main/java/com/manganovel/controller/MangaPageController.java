package com.manganovel.controller;

import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.MangaPage;
import com.manganovel.security.RequireRole;
import com.manganovel.security.WorkOwnerChecker;
import com.manganovel.service.IChapterService;
import com.manganovel.service.IMangaPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/manga-page")
@Tag(name = "漫画页管理")
public class MangaPageController {

    private final IMangaPageService mangaPageService;
    private final IChapterService chapterService;
    private final WorkOwnerChecker ownerChecker;

    public MangaPageController(IMangaPageService mangaPageService, IChapterService chapterService,
                               WorkOwnerChecker ownerChecker) {
        this.mangaPageService = mangaPageService;
        this.chapterService = chapterService;
        this.ownerChecker = ownerChecker;
    }

    @GetMapping("/list/{chapterId}")
    @Operation(summary = "查询章节的所有漫画页")
    public Result<List<MangaPage>> list(@PathVariable Long chapterId) {
        return Result.ok(mangaPageService.listByChapterId(chapterId));
    }

    @PostMapping("/batch")
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "批量导入漫画页（作者须为作品属主）")
    public Result<?> batchInsert(@RequestHeader("Authorization") String auth,
                                  @RequestBody Map<String, Object> body) {
        Long chapterId = Long.valueOf(body.get("chapterId").toString());
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) throw new BusinessException("章节不存在");
        ownerChecker.check(chapter.getWorkId(), auth);
        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) body.get("imageUrls");
        List<MangaPage> pages = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            MangaPage page = new MangaPage();
            page.setChapterId(chapterId);
            page.setPageNum(i + 1);
            page.setImageUrl(urls.get(i));
            pages.add(page);
        }
        mangaPageService.saveBatch(pages);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "删除单张漫画页（作者须为作品属主）")
    public Result<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        MangaPage page = mangaPageService.getById(id);
        if (page == null) throw new BusinessException("漫画页不存在");
        Chapter chapter = chapterService.getById(page.getChapterId());
        if (chapter == null) throw new BusinessException("章节不存在");
        ownerChecker.check(chapter.getWorkId(), auth);
        mangaPageService.removeById(id);
        return Result.ok();
    }
}
