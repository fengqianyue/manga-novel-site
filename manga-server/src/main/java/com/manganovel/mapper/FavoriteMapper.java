package com.manganovel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manganovel.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
