package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.ReadingProgress;
import com.manganovel.mapper.ReadingProgressMapper;
import com.manganovel.service.IReadingProgressService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ReadingProgressServiceImpl extends ServiceImpl<ReadingProgressMapper, ReadingProgress>
        implements IReadingProgressService {

    @Override
    public void save(Long userId, Long workId, Long chapterId, Integer pageNum) {
        // 先尝试 INSERT（效率高），唯一键冲突则 UPDATE（避免竞态）
        ReadingProgress rp = new ReadingProgress();
        rp.setUserId(userId);
        rp.setWorkId(workId);
        rp.setChapterId(chapterId);
        rp.setPageNum(pageNum);
        try {
            save(rp);
        } catch (DuplicateKeyException e) {
            lambdaUpdate()
                .eq(ReadingProgress::getUserId, userId)
                .eq(ReadingProgress::getWorkId, workId)
                .set(ReadingProgress::getChapterId, chapterId)
                .set(ReadingProgress::getPageNum, pageNum)
                .update();
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
