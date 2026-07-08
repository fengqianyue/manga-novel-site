package com.manganovel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manganovel.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
