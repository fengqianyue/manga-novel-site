package com.manganovel.service.impl;

import com.manganovel.common.BusinessException;
import com.manganovel.entity.User;
import com.manganovel.entity.Work;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommentService 单元测试
 * 覆盖：发表评论、字数限制、评论列表、删除自己的评论、删除他人的评论
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommentServiceImplTest {

    @Autowired
    private CommentServiceImpl commentService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private WorkServiceImpl workService;

    private Long userId;
    private Long workId;
    private Long otherUserId;

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // @TableLogic 会拦截所有 delete，必须用原生 SQL 做物理删除
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM comment");
            stmt.execute("DELETE FROM user");
            stmt.execute("DELETE FROM work");
        }

        // 创建测试用户
        User u = new User(); u.setUsername("评论者"); u.setEmail("c@test.com");
        u.setPassword("pass"); u.setRole(0);
        userService.save(u);
        userId = u.getId();

        User other = new User(); other.setUsername("他人"); other.setEmail("o@test.com");
        other.setPassword("pass"); other.setRole(0);
        userService.save(other);
        otherUserId = other.getId();

        // 创建测试作品
        Work w = new Work(); w.setTitle("测试作品"); w.setAuthor("作者"); w.setType("manga");
        w.setStatus(1); w.setCompleted(0); w.setViewCount(0L);
        workService.save(w);
        workId = w.getId();
    }

    // ==================== 发表评论 ====================

    @Test
    @Order(1)
    @DisplayName("发表评论 - 正常发表成功")
    void shouldAddCommentSuccessfully() {
        assertDoesNotThrow(() -> commentService.add(userId, workId, "这是一条测试评论"));

        List<Map<String, Object>> list = commentService.listByWorkId(workId);
        assertEquals(1, list.size());
        assertEquals("这是一条测试评论", list.get(0).get("content"));
        assertEquals("评论者", list.get(0).get("username"));
    }

    @Test
    @Order(2)
    @DisplayName("发表评论 - 空内容抛异常")
    void shouldRejectEmptyComment() {
        assertThrows(BusinessException.class, () -> commentService.add(userId, workId, ""));
        assertThrows(BusinessException.class, () -> commentService.add(userId, workId, "   "));
    }

    @Test
    @Order(3)
    @DisplayName("发表评论 - 超过500字抛异常")
    void shouldRejectOverLimitComment() {
        String longContent = "a".repeat(501);
        BusinessException ex = assertThrows(BusinessException.class, () ->
            commentService.add(userId, workId, longContent)
        );
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @Order(4)
    @DisplayName("发表评论 - 刚好500字可以发表")
    void shouldAccept500CharComment() {
        String exactContent = "a".repeat(500);
        assertDoesNotThrow(() -> commentService.add(userId, workId, exactContent));
    }

    // ==================== 评论列表 ====================

    @Test
    @Order(5)
    @DisplayName("评论列表 - 按时间倒序排列")
    void shouldListCommentsDescending() {
        commentService.add(userId, workId, "第一条");
        commentService.add(userId, workId, "第二条");

        List<Map<String, Object>> list = commentService.listByWorkId(workId);
        assertEquals(2, list.size());
        // 最新的在前（倒序）
        assertTrue(((java.time.LocalDateTime) list.get(0).get("createdAt"))
            .compareTo((java.time.LocalDateTime) list.get(1).get("createdAt")) >= 0);
    }

    @Test
    @Order(6)
    @DisplayName("评论列表 - 空作品无评论")
    void shouldReturnEmptyForNoComments() {
        List<Map<String, Object>> list = commentService.listByWorkId(9999L);
        assertTrue(list.isEmpty());
    }

    // ==================== 删除评论 ====================

    @Test
    @Order(7)
    @DisplayName("删除评论 - 本人可删除自己的评论")
    void shouldAllowDeleteOwnComment() {
        commentService.add(userId, workId, "我的评论");
        Long commentId = commentService.listByWorkId(workId).get(0).get("id") instanceof Integer
            ? ((Integer) commentService.listByWorkId(workId).get(0).get("id")).longValue()
            : (Long) commentService.listByWorkId(workId).get(0).get("id");

        assertDoesNotThrow(() -> commentService.remove(commentId, userId));
        assertTrue(commentService.listByWorkId(workId).isEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("删除评论 - 不能删除他人的评论")
    void shouldRejectDeleteOthersComment() {
        commentService.add(userId, workId, "别人的评论");
        Long commentId = (Long) commentService.listByWorkId(workId).get(0).get("id");

        BusinessException ex = assertThrows(BusinessException.class, () ->
            commentService.remove(commentId, otherUserId)
        );
        assertTrue(ex.getMessage().contains("只能删除自己的"));
    }

    @Test
    @Order(9)
    @DisplayName("删除评论 - 评论不存在抛异常")
    void shouldRejectDeleteNonExistentComment() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
            commentService.remove(99999L, userId)
        );
        assertTrue(ex.getMessage().contains("不存在"));
    }
}
