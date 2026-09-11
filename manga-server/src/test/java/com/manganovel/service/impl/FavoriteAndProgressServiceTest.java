package com.manganovel.service.impl;

import com.manganovel.entity.Favorite;
import com.manganovel.entity.ReadingProgress;
import com.manganovel.entity.User;
import com.manganovel.entity.Work;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FavoriteService + ReadingProgressService 单元测试
 * 覆盖：收藏/取消收藏、收藏列表、重复收藏、阅读进度 upsert
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FavoriteAndProgressServiceTest {

    @Autowired private FavoriteServiceImpl favoriteService;
    @Autowired private ReadingProgressServiceImpl progressService;
    @Autowired private UserServiceImpl userService;
    @Autowired private WorkServiceImpl workService;

    private Long userId;
    private Long workId;

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // @TableLogic 会拦截所有 delete，必须用原生 SQL 做物理删除
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM favorite");
            stmt.execute("DELETE FROM reading_progress");
            stmt.execute("DELETE FROM user");
            stmt.execute("DELETE FROM work");
        }

        User u = new User(); u.setUsername("test"); u.setEmail("t@t.com");
        u.setPassword("pass"); u.setRole(0); userService.save(u); userId = u.getId();

        Work w = new Work(); w.setTitle("作品"); w.setAuthor("作者"); w.setType("manga");
        w.setStatus(1); w.setCompleted(0); w.setViewCount(0L);
        workService.save(w); workId = w.getId();
    }

    // ==================== 收藏测试 ====================

    @Test
    @Order(1)
    @DisplayName("收藏 - 正常收藏成功")
    void shouldAddFavorite() {
        assertDoesNotThrow(() -> favoriteService.add(userId, workId));
        assertTrue(favoriteService.isFavorited(userId, workId));
    }

    @Test
    @Order(2)
    @DisplayName("收藏 - 取消收藏成功")
    void shouldRemoveFavorite() {
        favoriteService.add(userId, workId);
        assertTrue(favoriteService.isFavorited(userId, workId));

        favoriteService.remove(userId, workId);
        assertFalse(favoriteService.isFavorited(userId, workId));
    }

    @Test
    @Order(3)
    @DisplayName("收藏 - 重复收藏抛异常（唯一约束）")
    void shouldRejectDuplicateFavorite() {
        favoriteService.add(userId, workId);
        assertThrows(Exception.class, () -> favoriteService.add(userId, workId));
    }

    @Test
    @Order(4)
    @DisplayName("收藏 - 获取用户收藏列表")
    void shouldListUserFavorites() {
        // 创建第二个作品
        Work w2 = new Work(); w2.setTitle("作品2"); w2.setAuthor("作者"); w2.setType("novel");
        w2.setStatus(1); w2.setCompleted(0); w2.setViewCount(0L);
        workService.save(w2);

        favoriteService.add(userId, workId);
        favoriteService.add(userId, w2.getId());

        List<Favorite> list = favoriteService.listByUser(userId);
        assertEquals(2, list.size());
    }

    @Test
    @Order(5)
    @DisplayName("收藏 - 未登录/未收藏检查返回 false")
    void shouldReturnFalseForNotFavorited() {
        assertFalse(favoriteService.isFavorited(userId, workId));
    }

    // ==================== 阅读进度测试 ====================

    @Test
    @Order(6)
    @DisplayName("阅读进度 - 首次保存创建新记录")
    void shouldCreateNewProgress() {
        progressService.save(userId, workId, 1L, 5);
        ReadingProgress rp = progressService.getByUserAndWork(userId, workId);

        assertNotNull(rp);
        assertEquals(1L, rp.getChapterId());
        assertEquals(5, rp.getPageNum());
    }

    @Test
    @Order(7)
    @DisplayName("阅读进度 - 再次保存更新已有记录（upsert）")
    void shouldUpdateExistingProgress() {
        progressService.save(userId, workId, 1L, 3);
        progressService.save(userId, workId, 2L, 10);

        ReadingProgress rp = progressService.getByUserAndWork(userId, workId);
        assertEquals(2L, rp.getChapterId(), "章节应更新");
        assertEquals(10, rp.getPageNum(), "页码应更新");
    }

    @Test
    @Order(8)
    @DisplayName("阅读进度 - 无记录返回 null")
    void shouldReturnNullForNoProgress() {
        assertNull(progressService.getByUserAndWork(userId, workId));
    }

    @Test
    @Order(9)
    @DisplayName("阅读进度 - 不同用户独立")
    void progressShouldBeUserSpecific() {
        User u2 = new User(); u2.setUsername("user2"); u2.setEmail("u2@t.com");
        u2.setPassword("pass"); u2.setRole(0); userService.save(u2);

        progressService.save(userId, workId, 1L, 5);
        progressService.save(u2.getId(), workId, 3L, 20);

        assertEquals(1L, progressService.getByUserAndWork(userId, workId).getChapterId());
        assertEquals(3L, progressService.getByUserAndWork(u2.getId(), workId).getChapterId());
    }
}
