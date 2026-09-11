package com.manganovel.controller;

import com.manganovel.common.Result;
import com.manganovel.entity.NovelContent;
import com.manganovel.service.INovelContentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI 章节摘要（调用大模型 API）
 * 使用 OpenAI 兼容接口，默认指向 DeepSeek
 */
@RestController
@RequestMapping("/api/ai")
public class AiSummaryController {

    private final INovelContentService novelContentService;
    private final RestTemplate rest = new RestTemplate();

    @Value("${app.ai.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    public AiSummaryController(INovelContentService novelContentService) {
        this.novelContentService = novelContentService;
    }

    @PostMapping("/summary")
    @Operation(summary = "AI 生成章节摘要")
    public Result<Map<String, String>> generateSummary(@RequestBody Map<String, Object> body) {
        if (!enabled || apiKey.isEmpty()) {
            return Result.fail("AI 摘要服务未启用，请配置 app.ai.api-key");
        }

        Long chapterId = Long.valueOf(body.get("chapterId").toString());
        NovelContent content = novelContentService.getByChapterId(chapterId);
        if (content == null || content.getTextContent() == null || content.getTextContent().isBlank()) {
            return Result.fail("该章节暂无文本内容");
        }

        // 截取前 2000 字发给 AI（控制 token 消耗）
        String text = content.getTextContent().length() > 2000
            ? content.getTextContent().substring(0, 2000)
            : content.getTextContent();

        try {
            String summary = callAi(text);
            Map<String, String> result = new HashMap<>();
            result.put("summary", summary);
            return Result.ok(result);
        } catch (Exception e) {
            return Result.fail("AI 摘要生成失败，请稍后再试");
        }
    }

    @SuppressWarnings("unchecked")
    private String callAi(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", "deepseek-chat");
        request.put("temperature", 0.3);
        request.put("max_tokens", 200);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个小说章节摘要助手。请用3句话以内总结以下章节内容，简洁精炼。");
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "请总结以下章节内容：\n\n" + text);
        messages.add(userMsg);

        request.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = rest.exchange(
            apiUrl, HttpMethod.POST,
            new HttpEntity<>(request, headers), Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                return (String) msg.get("content");
            }
        }
        return "无法生成摘要";
    }
}
