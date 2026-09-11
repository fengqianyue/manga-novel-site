package com.manganovel.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.manganovel.entity.Work;
import com.manganovel.mapper.WorkMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class SearchService {

    private final WorkMapper workMapper;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.es.uri:http://localhost:9200}")
    private String esUri;

    private boolean esAvailable = false;

    public SearchService(WorkMapper workMapper) {
        this.workMapper = workMapper;
    }

    @PostConstruct
    void init() {
        try {
            ResponseEntity<String> resp = rest.getForEntity(esUri, String.class);
            esAvailable = resp.getStatusCode().is2xxSuccessful();
            log.info("ES 连接成功: {}", esUri);
        } catch (Exception e) {
            log.warn("ES 不可用 ({}), 降级 MySQL LIKE", e.getMessage());
        }
    }

    /** 搜索：优先 ES，不可用降级 MySQL */
    public List<Long> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        if (esAvailable) return esSearch(keyword);
        return mysqlSearch(keyword);
    }

    private List<Long> esSearch(String keyword) {
        try {
            // 按空格拆分关键词
            String[] terms = keyword.trim().split("\\s+");
            ObjectNode query = mapper.createObjectNode();
            ObjectNode bool = mapper.createObjectNode();
            ArrayNode must = mapper.createArrayNode();
            must.add(mapper.createObjectNode().set("term", mapper.createObjectNode().put("status", 1)));

            // 每个关键词：multi_match 精准匹配 + wildcard 兜底
            ArrayNode should = mapper.createArrayNode();
            for (String term : terms) {
                if (term.isBlank()) continue;
                ArrayNode fields = mapper.createArrayNode();
                fields.add("title^3").add("author^2").add("summary");
                should.add(mapper.createObjectNode().set("multi_match",
                        mapper.createObjectNode().put("query", term).set("fields", fields)));
                // wildcard 兜底：无空格中文词也能部分命中
                for (String f : new String[]{"title", "author"}) {
                    should.add(mapper.createObjectNode().set("wildcard",
                            mapper.createObjectNode().put(f, "*" + term + "*")));
                }
            }
            must.add(mapper.createObjectNode().set("bool",
                    mapper.createObjectNode().set("should", should)));
            bool.set("must", must);
            query.set("query", mapper.createObjectNode().set("bool", bool));
            query.put("size", 20);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = rest.exchange(
                    esUri + "/work/_search", HttpMethod.POST,
                    new HttpEntity<>(query.toString(), headers), String.class);
            JsonNode root = mapper.readTree(resp.getBody());
            List<Long> ids = new ArrayList<>();
            root.path("hits").path("hits").forEach(h -> {
                long id = h.path("_source").path("id").asLong();
                if (id > 0) ids.add(id);
            });
            return ids;
        } catch (Exception e) {
            log.warn("ES 搜索异常，降级 MySQL: {}", e.getMessage());
            esAvailable = false; // ES 不可用时标记，避免后续请求重复超时
            return mysqlSearch(keyword);
        }
    }

    private List<Long> mysqlSearch(String keyword) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Work::getStatus, 1).eq(Work::getIsPublic, 1);
        // 按空格拆分，每个词都去匹配 title 和 author
        String[] terms = keyword.trim().split("\\s+");
        wrapper.and(w -> {
            for (int i = 0; i < terms.length; i++) {
                String t = terms[i];
                if (i == 0) w.like(Work::getTitle, t).or().like(Work::getAuthor, t);
                else w.like(Work::getTitle, t).or().like(Work::getAuthor, t);
            }
        });
        return workMapper.selectList(wrapper).stream().map(Work::getId).toList();
    }

    /** 同步作品到 ES */
    public void indexWork(Work work) {
        if (!esAvailable) return;
        try {
            ObjectNode doc = mapper.createObjectNode();
            doc.put("id", work.getId());
            doc.put("title", work.getTitle());
            doc.put("author", work.getAuthor());
            doc.put("type", work.getType());
            doc.put("status", work.getStatus());
            doc.put("isPublic", work.getIsPublic() == null ? 0 : work.getIsPublic());
            doc.put("summary", work.getSummary() == null ? "" : work.getSummary());
            doc.put("coverUrl", work.getCoverUrl() == null ? "" : work.getCoverUrl());
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(esUri + "/work/_doc/" + work.getId(), HttpMethod.PUT,
                    new HttpEntity<>(doc.toString(), h), String.class);
        } catch (Exception e) {
            log.error("ES 索引失败 workId={}", work.getId(), e);
        }
    }

    /** 从 ES 删除 */
    public void deleteWork(Long workId) {
        if (!esAvailable) return;
        try { rest.delete(esUri + "/work/_doc/" + workId); } catch (Exception ignored) {}
    }

    /** 全量重建索引 */
    public void rebuildIndex(List<Work> works) {
        if (!esAvailable) return;
        try { rest.delete(esUri + "/work"); } catch (Exception ignored) {}
        for (Work w : works) {
            if (w.getStatus() != null && w.getStatus() == 1
                    && w.getIsPublic() != null && w.getIsPublic() == 1) indexWork(w);
        }
        log.info("ES 索引重建完成");
    }
}
