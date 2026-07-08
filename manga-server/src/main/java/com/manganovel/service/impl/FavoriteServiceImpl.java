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
        // 唯一约束 uk_user_work 防止重复收藏，重复插入会抛异常
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
