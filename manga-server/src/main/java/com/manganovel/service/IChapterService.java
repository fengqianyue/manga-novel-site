package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.Chapter;
import java.util.List;

public interface IChapterService extends IService<Chapter> {
    List<Chapter> listByWorkId(Long workId);
    Chapter getFirstChapter(Long workId);
    Long getPreChapterId(Long chapterId);
    Long getNextChapterId(Long chapterId);
}
