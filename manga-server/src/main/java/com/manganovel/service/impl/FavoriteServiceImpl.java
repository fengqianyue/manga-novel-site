package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.entity.Favorite;
import com.manganovel.mapper.FavoriteMapper;
import com.manganovel.service.IFavoriteService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements IFavoriteService {

    @Override
    public void add(Long userId, Long workId) {
        // 先检查是否已收藏，避免 DB 约束触发 500 错误
        if (isFavorited(userId, workId)) return;
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setWorkId(workId);
        save(f);
    }

    @Override
    public void remove(Long userId, Long workId) {
        lambdaUpdate()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getWorkId, workId)
                .remove();
    }

    @Override
    public List<Favorite> listByUser(Long userId) {
        return lambdaQuery().eq(Favorite::getUserId, userId).list();
    }

    @Override
    public boolean isFavorited(Long userId, Long workId) {
        return lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getWorkId, workId)
                .count() > 0;
    }
}
