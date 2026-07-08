package com.manganovel.controller;

import cn.hutool.core.io.FileUtil;
import com.manganovel.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.manganovel.common.Result;
import com.manganovel.security.JwtUtil;
import com.manganovel.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件管理")
public class FileController {

    @Value("${app.upload-path:uploads/}")
    private String uploadPath;

    private String basePath;
    private final JwtUtil jwtUtil;

    public FileController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void init() {
        // 将相对路径转为绝对路径，避免 Tomcat 临时目录干扰
        basePath = new File(uploadPath).getAbsolutePath();
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        log.info("文件上传根目录: {}", basePath);
    }

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOW_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    @PostMapping("/upload/avatar")
    @Operation(summary = "上传头像（用户）")
    public Result<Map<String, String>> uploadAvatar(@RequestHeader("Authorization") String auth,
                                                     @RequestParam("file") MultipartFile file) {
        jwtUtil.getUserId(auth.substring(7)); // 仅验证登录，不要求管理员
        return doUpload(file, "avatars");
    }

    @PostMapping("/upload")
    @RequireRole
    @Operation(summary = "上传图片（管理员）")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "common") String dir) {
        return doUpload(file, dir);
    }

    private Result<Map<String, String>> doUpload(MultipartFile file, String dir) {
        if (file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = FileUtil.extName(originalName).toLowerCase();
        if (!ALLOW_EXT.contains(ext)) {
            throw new BusinessException("不支持的图片格式：" + ext);
        }
        // 生成唯一文件名
        String newName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        // 按日期分目录
        String subDir = dir + "/" + java.time.LocalDate.now().toString().replace("-", "/");
        File targetDir = new File(basePath + subDir);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File targetFile = new File(targetDir, newName);
        try {
            file.transferTo(targetFile);
        } catch (Exception e) {
            log.error("文件上传失败: path={}", targetFile.getAbsolutePath(), e);
            throw new BusinessException("文件保存失败");
        }
        String url = subDir + "/" + newName;
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("name", originalName);
        return Result.ok(result);
    }

    @PostMapping("/upload/batch")
    @RequireRole
    @Operation(summary = "批量上传图片（管理员）")
    public Result<List<Map<String, String>>> uploadBatch(@RequestParam("files") List<MultipartFile> files,
                                                          @RequestParam(defaultValue = "common") String dir) {
        List<Map<String, String>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String originalName = file.getOriginalFilename();
            String ext = FileUtil.extName(originalName).toLowerCase();
            if (!ALLOW_EXT.contains(ext)) continue;
            String newName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            String subDir = dir + "/" + java.time.LocalDate.now().toString().replace("-", "/");
            File targetDir = new File(basePath + subDir);
            if (!targetDir.exists()) targetDir.mkdirs();
            try {
                file.transferTo(new File(targetDir, newName));
                Map<String, String> m = new HashMap<>();
                m.put("url", subDir + "/" + newName);
                m.put("name", originalName);
                results.add(m);
            } catch (Exception e) {
                // 跳过失败的文件，继续处理剩余的
            }
        }
        return Result.ok(results);
    }
}
