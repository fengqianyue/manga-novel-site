package com.manganovel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.Work;
import com.manganovel.mapper.WorkMapper;
import com.manganovel.service.IWorkService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkServiceImpl extends ServiceImpl<WorkMapper, Work> implements IWorkService {

    @Override
    public Page<Work> pageByType(String type, int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Work::getStatus, 1).isNull(Work::getUserId); // 只展示公开作品
        if (StringUtils.hasText(type)) {
            wrapper.eq(Work::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Work::getTitle, keyword).or().like(Work::getAuthor, keyword));
        }
        wrapper.orderByDesc(Work::getCreatedAt);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Page<Work> pageForAdmin(Integer status, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Work::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Work::getTitle, keyword).or().like(Work::getAuthor, keyword));
        }
        wrapper.orderByDesc(Work::getCreatedAt);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Page<Work> pageWithFilter(String type, String keyword, Integer publishYear, Integer completed,
                                      Long tagId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Work::getStatus, 1).isNull(Work::getUserId);
        if (StringUtils.hasText(type)) wrapper.eq(Work::getType, type);
        if (StringUtils.hasText(keyword))
            wrapper.and(w -> w.like(Work::getTitle, keyword).or().like(Work::getAuthor, keyword));
        if (publishYear != null) wrapper.eq(Work::getPublishYear, publishYear);
        if (completed != null) wrapper.eq(Work::getCompleted, completed);
        // tagId 为 Long 类型，拼接内容仅含数字，无注入风险
        if (tagId != null)
            wrapper.inSql(Work::getId, "SELECT work_id FROM work_tag WHERE tag_id = " + tagId);
        wrapper.orderByDesc(Work::getCreatedAt);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
