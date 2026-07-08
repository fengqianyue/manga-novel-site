package com.manganovel.controller;

import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.manganovel.dto.LoginRequest;
import com.manganovel.dto.RegisterRequest;
import com.manganovel.entity.User;
import com.manganovel.security.JwtUtil;
import com.manganovel.security.RequireRole;
import com.manganovel.service.IUserService;
import com.manganovel.vo.LoginVO;
import com.manganovel.vo.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理")
public class UserController {

    private final IUserService userService;
    private final JwtUtil jwtUtil;

    public UserController(IUserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.ok();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return Result.ok(buildLoginVO(user, token));
    }

    @PostMapping("/admin-login")
    @Operation(summary = "管理员登录（仅 role=1 可登录）")
    public Result<LoginVO> adminLogin(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request);
        if (user.getRole() != 1) {
            throw new BusinessException(403, "无管理员权限");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return Result.ok(buildLoginVO(user, token));
    }

    @GetMapping("/list")
    @RequireRole
    @Operation(summary = "用户列表（管理员）")
    public Result<List<UserVO>> list() {
        List<UserVO> vos = userService.list().stream()
                .map(u -> UserVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .avatarUrl(u.getAvatarUrl())
                        .role(u.getRole())
                        .build())
                .toList();
        return Result.ok(vos);
    }

    @PutMapping("/{id}/toggle")
    @RequireRole
    @Operation(summary = "启用/禁用用户（管理员）")
    public Result<?> toggleUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (user.getRole() == 1) throw new BusinessException("不能禁用管理员");
        user.setIsDeleted(user.getIsDeleted() == 1 ? 0 : 1);
        userService.updateById(user);
        return Result.ok();
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<?> changePassword(@RequestHeader("Authorization") String auth,
                                     @RequestBody Map<String, String> body) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        User user = userService.getById(userId);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(body.get("oldPassword"), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(encoder.encode(body.get("newPassword")));
        userService.updateById(user);
        return Result.ok();
    }

    @PutMapping("/avatar")
    @Operation(summary = "更新头像")
    public Result<String> updateAvatar(@RequestHeader("Authorization") String auth,
                                        @RequestBody Map<String, String> body) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        User user = userService.getById(userId);
        user.setAvatarUrl(body.get("avatarUrl"));
        userService.updateById(user);
        // 更新后重新签发 token（含新信息）
        return Result.ok("ok");
    }

    private LoginVO buildLoginVO(User user, String token) {
        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();
        return LoginVO.builder().user(userVO).token(token).build();
    }
}
