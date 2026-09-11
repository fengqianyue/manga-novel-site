package com.manganovel.controller;

import com.manganovel.common.BusinessException;
import com.manganovel.common.Result;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.NovelContent;
import com.manganovel.security.RequireRole;
import com.manganovel.service.IChapterService;
import com.manganovel.service.INovelContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/novel-content")
@Tag(name = "小说内容管理")
public class NovelContentController {

    private final INovelContentService contentService;
    private final IChapterService chapterService;

    /** 匹配 "数字+空格+标题" 的章节点 */
    private static final Pattern CHAPTER_PAT = Pattern.compile("^(\\d+)\\s+(.+)$", Pattern.MULTILINE);

    public NovelContentController(INovelContentService contentService, IChapterService chapterService) {
        this.contentService = contentService;
        this.chapterService = chapterService;
    }

    @GetMapping("/{chapterId}")
    @Operation(summary = "读取章节文本")
    public Result<NovelContent> get(@PathVariable Long chapterId) {
        NovelContent nc = contentService.getByChapterId(chapterId);
        if (nc == null) return Result.ok(null);
        return Result.ok(nc);
    }

    @PostMapping("/save")
    @RequireRole
    @Operation(summary = "保存章节文本")
    public Result<?> save(@RequestBody Map<String, Object> body) {
        Long chapterId = Long.valueOf(body.get("chapterId").toString());
        String text = body.get("textContent").toString();
        contentService.saveContent(chapterId, text);
        return Result.ok();
    }

    @PostMapping("/import")
    @RequireRole
    @Operation(summary = "导入 TXT 并自动分章")
    public Result<Map<String, Object>> importTxt(@RequestParam("file") MultipartFile file,
                                                  @RequestParam Long workId) {
        if (file.isEmpty()) throw new BusinessException("文件为空");
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("文件读取失败");
        }

        // 正则匹配所有分章点
        Matcher m = CHAPTER_PAT.matcher(content);
        List<int[]> boundaries = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            boundaries.add(new int[]{m.start(), m.end()});
            titles.add(m.group(2)); // 章节标题
        }

        if (boundaries.isEmpty()) {
            // 没有分章标记，整本作为一个章节
            Chapter ch = new Chapter();
            ch.setWorkId(workId);
            ch.setTitle("正文");
            ch.setChapterNum(1.0);
            chapterService.save(ch);
            contentService.saveContent(ch.getId(), content);
            Map<String, Object> result = new HashMap<>();
            result.put("chapters", 1);
            return Result.ok(result);
        }

        int count = 0;
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i)[0];
            int end = (i < boundaries.size() - 1) ? boundaries.get(i + 1)[0] : content.length();
            String chapterText = content.substring(start, end).trim();

            Chapter ch = new Chapter();
            ch.setWorkId(workId);
            ch.setTitle(titles.get(i));
            ch.setChapterNum((double) (i + 1));
            chapterService.save(ch);

            // 去掉章节标题行本身
            int firstNewline = chapterText.indexOf('\n');
            if (firstNewline > 0) {
                chapterText = chapterText.substring(firstNewline + 1).trim();
            }
            contentService.saveContent(ch.getId(), chapterText);
            count++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("chapters", count);
        result.put("titles", titles);
        return Result.ok(result);
    }
}
