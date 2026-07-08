package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.Favorite;
import com.manganovel.security.JwtUtil;
import com.manganovel.service.IFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorite")
@Tag(name = "收藏管理")
public class FavoriteController {

    private final IFavoriteService favoriteService;
    private final JwtUtil jwtUtil;

    public FavoriteController(IFavoriteService favoriteService, JwtUtil jwtUtil) {
        this.favoriteService = favoriteService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @Operation(summary = "收藏作品")
    public Result<?> add(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Long> body) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        favoriteService.add(userId, body.get("workId"));
        return Result.ok();
    }

    @DeleteMapping("/{workId}")
    @Operation(summary = "取消收藏")
    public Result<?> remove(@RequestHeader("Authorization") String auth, @PathVariable Long workId) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        favoriteService.remove(userId, workId);
        return Result.ok();
    }

    @GetMapping("/list")
    @Operation(summary = "当前用户的收藏列表")
    public Result<List<Long>> list(@RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        List<Long> workIds = favoriteService.listByUser(userId)
                .stream().map(Favorite::getWorkId).collect(Collectors.toList());
        return Result.ok(workIds);
    }

    @GetMapping("/check/{workId}")
    @Operation(summary = "检查是否已收藏")
    public Result<Boolean> check(@RequestHeader("Authorization") String auth, @PathVariable Long workId) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        return Result.ok(favoriteService.isFavorited(userId, workId));
    }
}
