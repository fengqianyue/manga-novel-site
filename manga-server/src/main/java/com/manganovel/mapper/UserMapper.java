package com.manganovel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manganovel.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
