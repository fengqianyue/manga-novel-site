package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.ReadingProgress;
import com.manganovel.mapper.ReadingProgressMapper;
import com.manganovel.service.IReadingProgressService;
import org.springframework.stereotype.Service;

@Service
public class ReadingProgressServiceImpl extends ServiceImpl<ReadingProgressMapper, ReadingProgress>
        implements IReadingProgressService {

    @Override
    public void save(Long userId, Long workId, Long chapterId, Integer pageNum) {
        // 先查是否存在该作品的进度，存在则更新，不存在则新增
        ReadingProgress existing = lambdaQuery()
                .eq(ReadingProgress::getUserId, userId)
                .eq(ReadingProgress::getWorkId, workId)
                .one();
        if (existing != null) {
            existing.setChapterId(chapterId);
            existing.setPageNum(pageNum);
            updateById(existing);
        } else {
            ReadingProgress rp = new ReadingProgress();
            rp.setUserId(userId);
            rp.setWorkId(workId);
            rp.setChapterId(chapterId);
            rp.setPageNum(pageNum);
            save(rp);
        }
    }

    @Override
    public ReadingProgress getByUserAndWork(Long userId, Long workId) {
        return lambdaQuery()
                .eq(ReadingProgress::getUserId, userId)
                .eq(ReadingProgress::getWorkId, workId)
                .one();
    }
}
