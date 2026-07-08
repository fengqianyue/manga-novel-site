package com.manganovel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.Chapter;
import com.manganovel.mapper.ChapterMapper;
import com.manganovel.service.IChapterService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChapterServiceImpl extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {

    @Override
    public List<Chapter> listByWorkId(Long workId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getWorkId, workId)
               .eq(Chapter::getStatus, 1)
               .orderByAsc(Chapter::getChapterNum);
        return list(wrapper);
    }

    @Override
    public Chapter getFirstChapter(Long workId) {
        return lambdaQuery().eq(Chapter::getWorkId, workId).eq(Chapter::getStatus, 1)
                .orderByAsc(Chapter::getChapterNum).last("LIMIT 1").one();
    }

    @Override
    public Long getPreChapterId(Long chapterId) {
        Chapter cur = getById(chapterId);
        if (cur == null) return null;
        Chapter pre = lambdaQuery().eq(Chapter::getWorkId, cur.getWorkId()).eq(Chapter::getStatus, 1)
                .lt(Chapter::getChapterNum, cur.getChapterNum())
                .orderByDesc(Chapter::getChapterNum).last("LIMIT 1").one();
        return pre != null ? pre.getId() : null;
    }

    @Override
    public Long getNextChapterId(Long chapterId) {
        Chapter cur = getById(chapterId);
        if (cur == null) return null;
        Chapter next = lambdaQuery().eq(Chapter::getWorkId, cur.getWorkId()).eq(Chapter::getStatus, 1)
                .gt(Chapter::getChapterNum, cur.getChapterNum())
                .orderByAsc(Chapter::getChapterNum).last("LIMIT 1").one();
        return next != null ? next.getId() : null;
    }
}
