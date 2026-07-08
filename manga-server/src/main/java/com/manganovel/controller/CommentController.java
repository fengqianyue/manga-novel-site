package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.security.JwtUtil;
import com.manganovel.service.ICommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comment")
@Tag(name = "评论管理")
public class CommentController {

    private final ICommentService commentService;
    private final JwtUtil jwtUtil;

    public CommentController(ICommentService commentService, JwtUtil jwtUtil) {
        this.commentService = commentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/list/{workId}")
    @Operation(summary = "作品评论列表")
    public Result<List<Map<String, Object>>> list(@PathVariable Long workId) {
        return Result.ok(commentService.listByWorkId(workId));
    }

    @PostMapping
    @Operation(summary = "发表评论")
    public Result<?> add(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> body) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        commentService.add(userId, Long.valueOf(body.get("workId").toString()), body.get("content").toString());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除评论")
    public Result<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        commentService.remove(id, userId);
        return Result.ok();
    }
}
