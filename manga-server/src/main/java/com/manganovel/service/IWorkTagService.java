package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.Tag;
import com.manganovel.entity.WorkTag;
import java.util.List;
import java.util.Map;

public interface IWorkTagService extends IService<WorkTag> {
    List<Tag> getTagsByWorkId(Long workId);
    void updateTags(Long workId, List<Long> tagIds);
    List<Map<String, Object>> getSimilarWorks(Long workId, String type, int limit);
}
