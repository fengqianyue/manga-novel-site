package com.manganovel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.manganovel.common.Result;
import com.manganovel.dto.WorkSaveDTO;
import com.manganovel.entity.Tag;
import com.manganovel.entity.Work;
import com.manganovel.search.SearchService;
import com.manganovel.security.RequireRole;
import com.manganovel.service.IWorkService;
import com.manganovel.service.IWorkTagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work")
@io.swagger.v3.oas.annotations.tags.Tag(name = "作品管理")
public class WorkController {

    private final IWorkService workService;
    private final IWorkTagService workTagService;
    private final SearchService searchService;
    private final com.manganovel.security.WorkOwnerChecker ownerChecker;

    public WorkController(IWorkService workService, IWorkTagService workTagService,
                          SearchService searchService,
                          com.manganovel.security.WorkOwnerChecker ownerChecker) {
        this.workService = workService;
        this.workTagService = workTagService;
        this.searchService = searchService;
        this.ownerChecker = ownerChecker;
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询作品列表")
    @Cacheable(value = "workList", key = "#type + '_' + #keyword + '_' + #pageNum + '_' + #pageSize")
    public Result<Page<Work>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "12") int pageSize) {
        return Result.ok(workService.pageByType(type, pageNum, pageSize, keyword));
    }

    @GetMapping("/recommend/{id}")
    @Operation(summary = "基于标签的内容推荐")
    public Result<List<Map<String, Object>>> recommend(@PathVariable Long id) {
        Work work = workService.getById(id);
        if (work == null) return Result.ok(List.of());
        return Result.ok(workTagService.getSimilarWorks(id, work.getType(), 6));
    }

    @GetMapping("/ranking")
    @Operation(summary = "排行榜（按浏览数，支持近三个月筛选）")
    @Cacheable(value = "ranking", key = "#type + '_' + #recent")
    public Result<List<Work>> ranking(@RequestParam(required = false) String type,
                                       @RequestParam(required = false, defaultValue = "false") boolean recent) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Work::getStatus, 1).eq(Work::getIsPublic, 1);
        if (org.springframework.util.StringUtils.hasText(type)) {
            wrapper.eq(Work::getType, type);
        }
        if (recent) {
            wrapper.ge(Work::getCreatedAt, java.time.LocalDateTime.now().minusMonths(3));
        }
        wrapper.orderByDesc(Work::getViewCount).last("LIMIT 20");
        return Result.ok(workService.list(wrapper));
    }

    @GetMapping("/filter")
    @Operation(summary = "多条件筛选作品")
    public Result<Page<Work>> filter(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer publishYear,
            @RequestParam(required = false) Integer completed,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(workService.pageWithFilter(type, keyword, publishYear, completed, tagId, pageNum, pageSize));
    }

    @GetMapping("/admin-list")
    @RequireRole
    @Operation(summary = "管理员作品列表（含上下架+搜索）")
    public Result<Page<Work>> adminList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(workService.pageForAdmin(status, keyword, pageNum, pageSize));
    }

    @DeleteMapping("/{id}")
    @RequireRole
    @Operation(summary = "删除作品（软删除）")
    public Result<?> delete(@PathVariable Long id) {
        workService.removeById(id);
        searchService.deleteWork(id);
        return Result.ok();
    }

    private void syncSearch(Work work) {
        try {
            // 只有"上架且公开发布"的作品进入索引，其余状态从索引移除
            if (work != null && work.getStatus() != null && work.getStatus() == 1
                    && work.getIsPublic() != null && work.getIsPublic() == 1) {
                searchService.indexWork(work);
            } else if (work != null) {
                searchService.deleteWork(work.getId());
            }
        } catch (Exception ignored) {}
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询作品详情")
    @Cacheable(value = "workDetail", key = "#id")
    public Result<Work> detail(@PathVariable Long id) {
        Work work = workService.getById(id);
        if (work == null) {
            return Result.fail("作品不存在");
        }
        // 浏览数原子 +1（避免读-改-写竞态）
        workService.lambdaUpdate().setSql("view_count = view_count + 1").eq(Work::getId, id).update();
        work.setViewCount((work.getViewCount() == null ? 0 : work.getViewCount()) + 1);
        return Result.ok(work);
    }

    @PostMapping
    @RequireRole
    @Operation(summary = "新增作品")
    @CacheEvict(value = {"workList", "ranking"}, allEntries = true)
    public Result<Long> add(@Valid @RequestBody WorkSaveDTO dto) {
        Work work = toEntity(dto);
        work.setId(null);
        workService.save(work);
        syncSearch(work);
        return Result.ok(work.getId());
    }

    @PutMapping
    @RequireRole
    @Operation(summary = "修改作品")
    @CacheEvict(value = {"workList", "workDetail", "ranking"}, allEntries = true)
    public Result<?> update(@Valid @RequestBody WorkSaveDTO dto) {
        Work work = toEntity(dto);
        workService.updateById(work);
        syncSearch(work);
        return Result.ok();
    }

    private Work toEntity(WorkSaveDTO dto) {
        Work work = new Work();
        work.setId(dto.getId());
        work.setTitle(dto.getTitle());
        work.setAuthor(dto.getAuthor());
        work.setCoverUrl(dto.getCoverUrl());
        work.setSummary(dto.getSummary());
        work.setType(dto.getType());
        work.setStatus(dto.getStatus());
        work.setIsPublic(1); // 管理员发布的作品默认为公开发布
        work.setPublishYear(dto.getPublishYear());
        work.setCompleted(dto.getCompleted());
        return work;
    }

    @PutMapping("/status")
    @RequireRole
    @Operation(summary = "上架/下架作品")
    @CacheEvict(value = {"workList", "workDetail", "ranking"}, allEntries = true)
    public Result<?> toggleStatus(@RequestParam Long id, @RequestParam Integer status) {
        Work work = new Work();
        work.setId(id);
        work.setStatus(status);
        workService.updateById(work);
        syncSearch(workService.getById(id));
        return Result.ok();
    }

    @PutMapping("/audit")
    @RequireRole
    @CacheEvict(value = {"workList", "workDetail", "ranking"}, allEntries = true)
    @Operation(summary = "审核作品（通过→上架 / 驳回→填理由）")
    public Result<?> audit(@RequestParam Long id,
                            @RequestParam boolean pass,
                            @RequestParam(required = false) String reason) {
        Work work = workService.getById(id);
        if (work == null) return Result.fail("作品不存在");
        if (work.getStatus() == null || work.getStatus() != 2) {
            return Result.fail("该作品不在待审核状态");
        }
        workService.lambdaUpdate()
                .eq(Work::getId, id)
                .set(Work::getStatus, pass ? 1 : 3)
                .set(Work::getRejectReason, pass ? null : (reason == null ? "" : reason.trim()))
                .update();
        if (pass) {
            syncSearch(workService.getById(id)); // 通过后进入搜索索引
        } else {
            searchService.deleteWork(id);
        }
        return Result.ok();
    }

    @GetMapping("/{id}/tags")
    @Operation(summary = "查询作品的标签")
    public Result<List<Tag>> getTags(@PathVariable Long id) {
        return Result.ok(workTagService.getTagsByWorkId(id));
    }

    @PutMapping("/{id}/tags")
    @RequireRole(1) // 管理员或作者
    @Operation(summary = "更新作品的标签（作者须为作品属主）")
    public Result<?> updateTags(@RequestHeader("Authorization") String auth,
                                 @PathVariable Long id, @RequestBody Map<String, Object> body) {
        ownerChecker.check(id, auth);
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("tagIds");
        List<Long> tagIds = rawIds.stream().map(Integer::longValue).toList();
        workTagService.updateTags(id, tagIds);
        return Result.ok();
    }
}
