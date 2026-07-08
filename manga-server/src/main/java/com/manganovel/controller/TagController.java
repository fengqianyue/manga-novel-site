package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.Tag;
import com.manganovel.security.RequireRole;
import com.manganovel.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
public class TagController {

    private final ITagService tagService;

    public TagController(ITagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有标签")
    public Result<List<Tag>> list() {
        return Result.ok(tagService.list());
    }

    @PostMapping
    @RequireRole
    @Operation(summary = "新增标签")
    public Result<?> add(@Valid @RequestBody Tag tag) {
        tag.setId(null);
        tagService.save(tag);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole
    @Operation(summary = "删除标签")
    public Result<?> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.ok();
    }
}
