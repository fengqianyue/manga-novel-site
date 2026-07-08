package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.entity.Comment;
import java.util.List;
import java.util.Map;

public interface ICommentService extends IService<Comment> {
    List<Map<String, Object>> listByWorkId(Long workId);
    void add(Long userId, Long workId, String content);
    void remove(Long commentId, Long userId);
}
