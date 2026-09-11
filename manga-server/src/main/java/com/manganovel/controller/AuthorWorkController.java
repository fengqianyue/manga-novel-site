package com.manganovel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.manganovel.dto.WorkSaveDTO;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.Work;
import com.manganovel.search.SearchService;
import com.manganovel.security.JwtUtil;
import com.manganovel.security.RequireRole;
import com.manganovel.service.IChapterService;
import com.manganovel.service.IWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

/**
 * 作者中心：作者对自己名下公开发布作品的管理入口。
 * 状态机：0=草稿/下架 → 2=待审核 → 1=上架（管理员通过） / 3=已驳回（可修改后重新提交）
 */
@RestController
@RequestMapping("/api/author")
@RequireRole(1) // 作者或管理员
@Tag(name = "作者中心")
public class AuthorWorkController {

    private final IWorkService workService;
    private final IChapterService chapterService;
    private final JwtUtil jwtUtil;
    private final SearchService searchService;

    public AuthorWorkController(IWorkService workService, IChapterService chapterService,
                                JwtUtil jwtUtil, SearchService searchService) {
        this.workService = workService;
        this.chapterService = chapterService;
        this.jwtUtil = jwtUtil;
        this.searchService = searchService;
    }

    @GetMapping("/works")
    @Operation(summary = "我的作品列表（按状态筛选）")
    public Result<Page<Work>> myWorks(@RequestHeader("Authorization") String auth,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = currentUserId(auth);
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Work::getUserId, userId).eq(Work::getIsPublic, 1);
        if (status != null) wrapper.eq(Work::getStatus, status);
        wrapper.orderByDesc(Work::getUpdatedAt);
        return Result.ok(workService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @PostMapping("/works")
    @Operation(summary = "创建作品草稿")
    public Result<Long> create(@RequestHeader("Authorization") String auth,
                                @Valid @RequestBody WorkSaveDTO dto) {
        Long userId = currentUserId(auth);
        Work work = new Work();
        copyFields(dto, work);
        work.setId(null);
        work.setUserId(userId);
        work.setIsPublic(1);   // 正式公开发布作品
        work.setStatus(0);     // 草稿
        work.setViewCount(0L);
        workService.save(work);
        return Result.ok(work.getId());
    }

    @PutMapping("/works")
    @Operation(summary = "编辑自己的作品（草稿/已驳回/已下架状态）")
    public Result<?> update(@RequestHeader("Authorization") String auth,
                             @Valid @RequestBody WorkSaveDTO dto) {
        Long userId = currentUserId(auth);
        if (dto.getId() == null) throw new BusinessException("缺少作品ID");
        Work work = workService.getById(dto.getId());
        if (work == null || !userId.equals(work.getUserId())) {
            throw new BusinessException(403, "只能编辑自己的作品");
        }
        if (work.getStatus() == 2) throw new BusinessException("待审核作品请先撤回再编辑");
        if (work.getStatus() == 1) throw new BusinessException("已上架作品请先下架再编辑");
        copyFields(dto, work); // 保留 userId/status/isPublic/viewCount
        workService.updateById(work);
        return Result.ok();
    }

    @PutMapping("/works/{id}/submit")
    @Operation(summary = "提交审核（草稿/已驳回 → 待审核）")
    public Result<?> submit(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Work work = ownWork(auth, id);
        if (work.getStatus() == null || (work.getStatus() != 0 && work.getStatus() != 3)) {
            throw new BusinessException("当前状态不可提交审核");
        }
        long chapters = chapterService.lambdaQuery().eq(Chapter::getWorkId, id).count();
        if (chapters == 0) throw new BusinessException("请先添加至少一个章节");
        workService.lambdaUpdate()
                .eq(Work::getId, id)
                .set(Work::getStatus, 2)
                .set(Work::getRejectReason, null)
                .update();
        return Result.ok();
    }

    @PutMapping("/works/{id}/withdraw")
    @Operation(summary = "撤回审核（待审核 → 草稿）")
    public Result<?> withdraw(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Work work = ownWork(auth, id);
        if (work.getStatus() == null || work.getStatus() != 2) {
            throw new BusinessException("只有待审核作品可以撤回");
        }
        workService.lambdaUpdate().eq(Work::getId, id).set(Work::getStatus, 0).update();
        return Result.ok();
    }

    @PutMapping("/works/{id}/offline")
    @CacheEvict(value = {"workList", "workDetail", "ranking"}, allEntries = true)
    @Operation(summary = "下架自己的已上架作品")
    public Result<?> offline(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Work work = ownWork(auth, id);
        if (work.getStatus() == null || work.getStatus() != 1) {
            throw new BusinessException("只有已上架作品可以下架");
        }
        workService.lambdaUpdate().eq(Work::getId, id).set(Work::getStatus, 0).update();
        searchService.deleteWork(id); // 从搜索索引移除
        return Result.ok();
    }

    // ==================== 内部方法 ====================

    private Long currentUserId(String auth) {
        return jwtUtil.getUserId(auth.startsWith("Bearer ") ? auth.substring(7) : auth);
    }

    /** 取自己的作品并校验属主，非属主抛 403 */
    private Work ownWork(String auth, Long id) {
        Long userId = currentUserId(auth);
        Work work = workService.getById(id);
        if (work == null) throw new BusinessException("作品不存在");
        if (!userId.equals(work.getUserId())) throw new BusinessException(403, "只能操作自己的作品");
        return work;
    }

    private void copyFields(WorkSaveDTO dto, Work work) {
        work.setTitle(dto.getTitle());
        work.setAuthor(dto.getAuthor());
        work.setCoverUrl(dto.getCoverUrl());
        work.setSummary(dto.getSummary());
        work.setType(dto.getType());
        work.setPublishYear(dto.getPublishYear());
        work.setCompleted(dto.getCompleted());
    }
}
