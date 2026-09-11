package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.common.BusinessException;
import com.manganovel.entity.Comment;
import com.manganovel.entity.User;
import com.manganovel.mapper.CommentMapper;
import com.manganovel.mapper.UserMapper;
import com.manganovel.service.ICommentService;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    private final UserMapper userMapper;

    public CommentServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<Map<String, Object>> listByWorkId(Long workId) {
        List<Comment> list = lambdaQuery()
                .eq(Comment::getWorkId, workId)
                .orderByDesc(Comment::getCreatedAt)
                .list();
        // 批量加载用户，避免 N+1 查询
        Set<Long> userIds = list.stream().map(Comment::getUserId).collect(Collectors.toSet());
        // 空集合会导致 selectBatchIds 生成 WHERE id IN () 非法 SQL，必须守卫
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment c : list) {
            User u = userMap.get(c.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId()); m.put("workId", c.getWorkId()); m.put("userId", c.getUserId());
            m.put("username", u != null ? u.getUsername() : "未知");
            m.put("avatarUrl", u != null ? u.getAvatarUrl() : null);
            m.put("content", c.getContent()); m.put("createdAt", c.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    @Override
    public void add(Long userId, Long workId, String content) {
        if (content == null || content.isBlank()) throw new BusinessException("评论内容不能为空");
        if (content.length() > 500) throw new BusinessException("评论不能超过500字");
        Comment c = new Comment();
        c.setUserId(userId); c.setWorkId(workId); c.setContent(content.trim());
        save(c);
    }

    @Override
    public void remove(Long commentId, Long userId) {
        Comment c = getById(commentId);
        if (c == null) throw new BusinessException("评论不存在");
        if (!c.getUserId().equals(userId)) throw new BusinessException("只能删除自己的评论");
        removeById(commentId);
    }
}
