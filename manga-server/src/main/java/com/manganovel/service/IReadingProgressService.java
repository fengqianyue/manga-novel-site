package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.ReadingProgress;

public interface IReadingProgressService extends IService<ReadingProgress> {
    void save(Long userId, Long workId, Long chapterId, Integer pageNum);
    ReadingProgress getByUserAndWork(Long userId, Long workId);
}
