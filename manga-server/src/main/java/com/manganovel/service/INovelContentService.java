package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.NovelContent;

public interface INovelContentService extends IService<NovelContent> {
    NovelContent getByChapterId(Long chapterId);
    void saveContent(Long chapterId, String text);
}
