package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.manganovel.entity.Work;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkService 单元测试
 * 覆盖：公开列表、管理员列表、多条件筛选、分页、状态过滤
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkServiceImplTest {

    @Autowired
    private WorkServiceImpl workService;

    @BeforeEach
    void setUp() {
        workService.lambdaUpdate().remove();
    }

    // ==================== 公开列表测试 ====================

    @Test
    @Order(1)
    @DisplayName("公开列表 - 仅返回 status=1 且 userId IS NULL 的作品")
    void shouldOnlyShowPublicWorks() {
        // 上架公开作品
        Work pub = createWork("公开作品", "manga", 1, null);
        // 下架作品
        Work hidden = createWork("下架作品", "manga", 0, null);
        // 私人作品
        Work priv = createWork("私人作品", "manga", 1, 99L);

        Page<Work> result = workService.pageByType(null, 1, 10, null);
        assertEquals(1, result.getTotal());
        assertEquals("公开作品", result.getRecords().get(0).getTitle());
    }

    @Test
    @Order(2)
    @DisplayName("公开列表 - 按类型过滤")
    void shouldFilterByType() {
        createWork("漫画A", "manga", 1, null);
        createWork("小说B", "novel", 1, null);

        Page<Work> mangaResult = workService.pageByType("manga", 1, 10, null);
        assertEquals(1, mangaResult.getTotal());
        assertEquals("漫画A", mangaResult.getRecords().get(0).getTitle());
    }

    @Test
    @Order(3)
    @DisplayName("公开列表 - 按关键字搜索标题/作者")
    void shouldSearchByKeyword() {
        createWork("进击的巨人", "manga", 1, null);
        createWork("鬼灭之刃", "manga", 1, null);
        createWork("其他", "novel", 1, null);

        Page<Work> result = workService.pageByType(null, 1, 10, "巨人");
        assertEquals(1, result.getTotal());
    }

    // ==================== 管理员列表测试 ====================

    @Test
    @Order(4)
    @DisplayName("管理员列表 - 可看到所有作品（含下架和私人）")
    void adminShouldSeeAllWorks() {
        createWork("公开", "manga", 1, null);
        createWork("下架", "manga", 0, null);
        createWork("私人", "novel", 1, 5L);

        Page<Work> result = workService.pageForAdmin(null, null, 1, 10);
        assertEquals(3, result.getTotal());
    }

    @Test
    @Order(5)
    @DisplayName("管理员列表 - 按状态筛选")
    void adminShouldFilterByStatus() {
        createWork("上架A", "manga", 1, null);
        createWork("上架B", "novel", 1, null);
        createWork("下架C", "manga", 0, null);

        assertEquals(2, workService.pageForAdmin(1, null, 1, 10).getTotal());
        assertEquals(1, workService.pageForAdmin(0, null, 1, 10).getTotal());
    }

    // ==================== 多条件筛选测试 ====================

    @Test
    @Order(6)
    @DisplayName("筛选 - 按出版年份 + 完结状态组合筛选")
    void shouldFilterByYearAndCompleted() {
        Work w1 = createWork("旧作未完", "manga", 1, null);
        w1.setPublishYear(2020); w1.setCompleted(0); workService.updateById(w1);

        Work w2 = createWork("旧作完结", "manga", 1, null);
        w2.setPublishYear(2020); w2.setCompleted(1); workService.updateById(w2);

        Work w3 = createWork("新作", "novel", 1, null);
        w3.setPublishYear(2024); w3.setCompleted(0); workService.updateById(w3);

        Page<Work> result = workService.pageWithFilter(null, null, 2020, 1, 1, 10);
        assertEquals(1, result.getTotal());
        assertEquals("旧作完结", result.getRecords().get(0).getTitle());
    }

    // ==================== 辅助方法 ====================

    private Work createWork(String title, String type, int status, Long userId) {
        Work w = new Work();
        w.setTitle(title);
        w.setAuthor("测试作者");
        w.setType(type);
        w.setStatus(status);
        w.setUserId(userId);
        w.setCompleted(0);
        w.setViewCount(0L);
        workService.save(w);
        return w;
    }
}
