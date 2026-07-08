package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.NovelContent;
import com.manganovel.mapper.NovelContentMapper;
import com.manganovel.service.INovelContentService;
import org.springframework.stereotype.Service;

@Service
public class NovelContentServiceImpl extends ServiceImpl<NovelContentMapper, NovelContent> implements INovelContentService {

    @Override
    public NovelContent getByChapterId(Long chapterId) {
        return lambdaQuery().eq(NovelContent::getChapterId, chapterId).one();
    }

    @Override
    public void saveContent(Long chapterId, String text) {
        NovelContent existing = getByChapterId(chapterId);
        if (existing != null) {
            existing.setTextContent(text);
            updateById(existing);
        } else {
            NovelContent nc = new NovelContent();
            nc.setChapterId(chapterId);
            nc.setPageNum(1);
            nc.setTextContent(text);
            save(nc);
        }
    }
}
