package com.manganovel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.manganovel.common.Result;
import com.manganovel.entity.Work;
import com.manganovel.search.SearchService;
import com.manganovel.service.IWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Tag(name = "搜索")
public class SearchController {

    private final SearchService searchService;
    private final IWorkService workService;

    public SearchController(SearchService searchService, IWorkService workService) {
        this.searchService = searchService;
        this.workService = workService;
    }

    @GetMapping
    @Operation(summary = "全文搜索作品")
    public Result<List<Work>> search(@RequestParam String keyword) {
        List<Long> ids = searchService.search(keyword);
        if (ids.isEmpty()) return Result.ok(List.of());
        // 按 ES 返回的顺序组装结果
        List<Work> works = workService.listByIds(ids);
        if (works.isEmpty()) return Result.ok(List.of());
        works.sort((a, b) -> ids.indexOf(a.getId()) - ids.indexOf(b.getId()));
        return Result.ok(works);
    }
}
