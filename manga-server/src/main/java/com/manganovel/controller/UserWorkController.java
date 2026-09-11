package com.manganovel.controller;

import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.MangaPage;
import com.manganovel.entity.NovelContent;
import com.manganovel.entity.Work;
import com.manganovel.security.JwtUtil;
import com.manganovel.service.IChapterService;
import com.manganovel.service.IMangaPageService;
import com.manganovel.service.INovelContentService;
import com.manganovel.service.IWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/user-work")
@Tag(name = "用户私人作品")
public class UserWorkController {

    private final IWorkService workService;
    private final IChapterService chapterService;
    private final IMangaPageService mangaPageService;
    private final INovelContentService novelContentService;
    private final JwtUtil jwtUtil;

    @Value("${app.upload-path:uploads/}")
    private String uploadPath;
    private String basePath;

    @PostConstruct
    public void init() {
        basePath = new File(uploadPath).getAbsolutePath();
        if (!basePath.endsWith(File.separator)) basePath += File.separator;
    }

    public UserWorkController(IWorkService workService, IChapterService chapterService,
                               IMangaPageService mangaPageService, INovelContentService novelContentService,
                               JwtUtil jwtUtil) {
        this.workService = workService;
        this.chapterService = chapterService;
        this.mangaPageService = mangaPageService;
        this.novelContentService = novelContentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/list")
    @Operation(summary = "我的书架")
    public Result<List<Work>> list(@RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        // 书架只展示私人导入作品，正式发布作品在作者中心管理
        List<Work> works = workService.lambdaQuery().eq(Work::getUserId, userId)
                .eq(Work::getIsPublic, 0)
                .eq(Work::getIsDeleted, 0).orderByDesc(Work::getCreatedAt).list();
        return Result.ok(works);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除私人作品（硬删除+清理文件）")
    public Result<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        Work work = workService.getById(id);
        if (work == null || !userId.equals(work.getUserId())) {
            throw new BusinessException("无权操作");
        }
        // 正式发布作品不可通过书架入口删除，请在作者中心下架或联系管理员
        if (work.getIsPublic() != null && work.getIsPublic() == 1) {
            throw new BusinessException("正式发布作品请在作者中心管理");
        }
        // 删除所有关联数据（物理删除）
        List<Chapter> chapters = chapterService.lambdaQuery().eq(Chapter::getWorkId, id).list();
        for (Chapter ch : chapters) {
            mangaPageService.getBaseMapper().delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MangaPage>()
                            .eq(MangaPage::getChapterId, ch.getId()));
            novelContentService.getBaseMapper().delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NovelContent>()
                            .eq(NovelContent::getChapterId, ch.getId()));
            chapterService.getBaseMapper().deleteById(ch.getId());
        }
        workService.getBaseMapper().deleteById(id);
        // 尝试删除磁盘文件
        try {
            File workDir = new File(basePath + "manga/" + id);
            if (workDir.exists()) deleteDir(workDir);
        } catch (Exception ignored) {}
        return Result.ok();
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }

    @PostMapping("/import-novel")
    @Operation(summary = "导入本地 TXT 小说")
    public Result<Map<String, Object>> importNovel(@RequestHeader("Authorization") String auth,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestParam(defaultValue = "") String title,
                                                    @RequestParam(defaultValue = "") String author) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        String content;
        try { content = new String(file.getBytes(), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new BusinessException("文件读取失败"); }

        // 默认标题/作者
        String fn = file.getOriginalFilename();
        if (fn == null) fn = "unknown.txt";
        if (title.isBlank()) title = fn.replaceAll("\\.txt$", "");
        if (author.isBlank()) author = "佚名";

        Work work = new Work();
        work.setTitle(title);
        work.setAuthor(author);
        work.setType("novel");
        work.setStatus(1);
        work.setUserId(userId);
        work.setIsPublic(0); // 私人书架作品，不进入公共列表
        workService.save(work);

        // 按章节标记拆分
        int count = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s+(.+)$", java.util.regex.Pattern.MULTILINE).matcher(content);
        List<int[]> bounds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) { bounds.add(new int[]{m.start(), m.end()}); titles.add(m.group(2)); }

        if (bounds.isEmpty()) {
            Chapter ch = new Chapter(); ch.setWorkId(work.getId()); ch.setTitle("正文"); ch.setChapterNum(1.0);
            chapterService.save(ch);
            novelContentService.saveContent(ch.getId(), content);
            count = 1;
        } else {
            for (int i = 0; i < bounds.size(); i++) {
                int start = bounds.get(i)[0];
                int end = (i < bounds.size() - 1) ? bounds.get(i + 1)[0] : content.length();
                String chText = content.substring(start, end).trim();
                Chapter ch = new Chapter(); ch.setWorkId(work.getId()); ch.setTitle(titles.get(i)); ch.setChapterNum((double)(i + 1));
                chapterService.save(ch);
                int nl = chText.indexOf('\n');
                if (nl > 0) chText = chText.substring(nl + 1).trim();
                novelContentService.saveContent(ch.getId(), chText);
                count++;
            }
        }
        Map<String, Object> r = new HashMap<>(); r.put("workId", work.getId()); r.put("chapters", count);
        return Result.ok(r);
    }

    @PostMapping("/import-manga")
    @Operation(summary = "导入本地漫画图片")
    public Result<Map<String, Object>> importManga(@RequestHeader("Authorization") String auth,
                                                    @RequestParam("files") List<MultipartFile> files,
                                                    @RequestParam(defaultValue = "") String title,
                                                    @RequestParam(defaultValue = "") String author) {
        Long userId = jwtUtil.getUserId(auth.substring(7));
        if (title.isBlank()) title = "导入漫画 " + System.currentTimeMillis() % 10000;
        if (author.isBlank()) author = "佚名";
        Work work = new Work();
        work.setTitle(title); work.setAuthor(author); work.setType("manga"); work.setStatus(1);
        work.setUserId(userId);
        work.setIsPublic(0); // 私人书架作品，不进入公共列表
        workService.save(work);

        Chapter ch = new Chapter(); ch.setWorkId(work.getId()); ch.setTitle("第1话"); ch.setChapterNum(1.0);
        chapterService.save(ch);

        String baseDir = "manga/" + work.getId() + "/ch" + ch.getId();
        File dir = new File(basePath + baseDir);
        if (!dir.exists()) dir.mkdirs();

        boolean firstIsCover = false;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f.isEmpty()) continue;
            String ext = cn.hutool.core.io.FileUtil.extName(f.getOriginalFilename()).toLowerCase();
            if (!Set.of("jpg","jpeg","png","webp").contains(ext)) continue;
            String name = String.format("%02d.%s", i + 1, ext);
            try { f.transferTo(new File(dir, name)); } catch (Exception ignored) {}
            // 第一张图设为封面
            if (!firstIsCover) {
                work.setCoverUrl(baseDir + "/" + name);
                workService.updateById(work);
                firstIsCover = true;
            }
            MangaPage page = new MangaPage(); page.setChapterId(ch.getId()); page.setPageNum(i + 1);
            page.setImageUrl(baseDir + "/" + name);
            mangaPageService.save(page);
        }
        Map<String, Object> r = new HashMap<>(); r.put("workId", work.getId()); r.put("pages", files.size());
        return Result.ok(r);
    }
}
