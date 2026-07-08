package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.Favorite;
import java.util.List;

public interface IFavoriteService extends IService<Favorite> {
    void add(Long userId, Long workId);
    void remove(Long userId, Long workId);
    List<Favorite> listByUser(Long userId);
    boolean isFavorited(Long userId, Long workId);
}
