package com.manganovel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.Tag;
import com.manganovel.entity.Work;
import com.manganovel.entity.WorkTag;
import com.manganovel.mapper.TagMapper;
import com.manganovel.mapper.WorkMapper;
import com.manganovel.mapper.WorkTagMapper;
import com.manganovel.service.IWorkTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class WorkTagServiceImpl extends ServiceImpl<WorkTagMapper, WorkTag> implements IWorkTagService {

    private final TagMapper tagMapper;
    private final WorkMapper workMapper;

    public WorkTagServiceImpl(TagMapper tagMapper, WorkMapper workMapper) {
        this.tagMapper = tagMapper;
        this.workMapper = workMapper;
    }

    @Override
    public List<Tag> getTagsByWorkId(Long workId) {
        List<Long> tagIds = lambdaQuery()
                .eq(WorkTag::getWorkId, workId)
                .list()
                .stream()
                .map(WorkTag::getTagId)
                .toList();
        if (tagIds.isEmpty()) return List.of();
        return tagMapper.selectBatchIds(tagIds);
    }

    @Override
    public List<Map<String, Object>> getSimilarWorks(Long workId, String type, int limit) {
        List<Map<String, Object>> result = getBaseMapper().selectSimilarWorks(workId, type, limit * 3);
        // 兜底：同标签不够时，用同类型最新作品填充到至少 limit 部
        if (result.size() < limit) {
            Set<Long> existingIds = new java.util.HashSet<>();
            existingIds.add(workId);
            for (Map<String, Object> m : result) existingIds.add((Long) m.get("id"));
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Work> wq =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wq.eq(Work::getStatus, 1).eq(Work::getType, type).isNull(Work::getUserId)
              .notIn(!existingIds.isEmpty(), Work::getId, existingIds)
              .orderByDesc(Work::getCreatedAt)
              .last("LIMIT " + (limit - result.size()));
            List<Work> fillWorks = workMapper.selectList(wq);
            for (Work w : fillWorks) {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", w.getId()); m.put("title", w.getTitle()); m.put("author", w.getAuthor());
                m.put("cover_url", w.getCoverUrl()); m.put("type", w.getType()); m.put("match_count", 0L);
                result.add(m);
            }
        }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    @Override
    @Transactional
    public void updateTags(Long workId, List<Long> tagIds) {
        LambdaQueryWrapper<WorkTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkTag::getWorkId, workId);
        getBaseMapper().delete(wrapper);
        for (Long tagId : tagIds) {
            WorkTag wt = new WorkTag();
            wt.setWorkId(workId);
            wt.setTagId(tagId);
            save(wt);
        }
    }
}
