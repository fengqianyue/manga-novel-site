package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.MangaPage;
import com.manganovel.security.RequireRole;
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

    public MangaPageController(IMangaPageService mangaPageService) {
        this.mangaPageService = mangaPageService;
    }

    @GetMapping("/list/{chapterId}")
    @Operation(summary = "查询章节的所有漫画页")
    public Result<List<MangaPage>> list(@PathVariable Long chapterId) {
        return Result.ok(mangaPageService.listByChapterId(chapterId));
    }

    @PostMapping("/batch")
    @RequireRole
    @Operation(summary = "批量导入漫画页")
    public Result<?> batchInsert(@RequestBody Map<String, Object> body) {
        Long chapterId = Long.valueOf(body.get("chapterId").toString());
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
    @RequireRole
    @Operation(summary = "删除单张漫画页")
    public Result<?> delete(@PathVariable Long id) {
        mangaPageService.removeById(id);
        return Result.ok();
    }
}
