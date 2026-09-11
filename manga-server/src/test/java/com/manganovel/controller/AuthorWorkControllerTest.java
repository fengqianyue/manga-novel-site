package com.manganovel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.manganovel.common.BusinessException;
import com.manganovel.dto.WorkSaveDTO;
import com.manganovel.entity.Chapter;
import com.manganovel.entity.User;
import com.manganovel.entity.Work;
import com.manganovel.security.JwtUtil;
import com.manganovel.service.impl.UserServiceImpl;
import com.manganovel.service.impl.WorkServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 作者中心工作流测试：创建草稿 → 提交审核 → 撤回 → 下架 → 越权防护
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorWorkControllerTest {

    @Autowired private AuthorWorkController authorController;
    @Autowired private WorkServiceImpl workService;
    @Autowired private UserServiceImpl userService;
    @Autowired private com.manganovel.service.impl.ChapterServiceImpl chapterService;
    @Autowired private JwtUtil jwtUtil;

    @Autowired
    private javax.sql.DataSource dataSource;

    private Long authorAId;
    private Long authorBId;
    private String authA;
    private String authB;

    @BeforeEach
    void setUp() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM chapter");
            stmt.execute("DELETE FROM work");
            stmt.execute("DELETE FROM user");
        }
        authorAId = createUser("authorA", 1);
        authorBId = createUser("authorB", 1);
        authA = "Bearer " + jwtUtil.generateToken(authorAId, "authorA", 1);
        authB = "Bearer " + jwtUtil.generateToken(authorBId, "authorB", 1);
    }

    private Long createUser(String name, int role) {
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@t.com");
        u.setPassword("pass");
        u.setRole(role);
        userService.save(u);
        return u.getId();
    }

    private WorkSaveDTO dto(String title, String type) {
        WorkSaveDTO d = new WorkSaveDTO();
        d.setTitle(title);
        d.setAuthor("测试作者");
        d.setType(type);
        d.setCompleted(0);
        return d;
    }

    private Long newDraft(String title) {
        var res = authorController.create(authA, dto(title, "manga"));
        return res.getData();
    }

    private void addChapter(Long workId) {
        Chapter ch = new Chapter();
        ch.setWorkId(workId);
        ch.setTitle("第1话");
        ch.setChapterNum(1.0);
        ch.setStatus(1);
        chapterService.save(ch);
        assertNotNull(ch.getId(), "章节应保存成功");
    }

    // ==================== 用例 ====================

    @Test
    @Order(1)
    @DisplayName("作者创建草稿 - is_public=1 且 status=0 归属本人")
    void shouldCreateDraft() {
        Long id = newDraft("作者作品");
        Work w = workService.getById(id);
        assertNotNull(w);
        assertEquals(0, w.getStatus(), "草稿状态应为0");
        assertEquals(1, w.getIsPublic(), "应为公开发布作品");
        assertEquals(authorAId, w.getUserId(), "应归属创建者");
    }

    @Test
    @Order(2)
    @DisplayName("提交审核 - 无章节时拒绝提交")
    void shouldRejectSubmitWithoutChapters() {
        Long id = newDraft("空作品");
        assertThrows(BusinessException.class, () -> authorController.submit(authA, id));
    }

    @Test
    @Order(3)
    @DisplayName("提交审核 - 有章节后状态变为待审核")
    void shouldSubmitToPending() {
        Long id = newDraft("可提交作品");
        addChapter(id);
        authorController.submit(authA, id);
        assertEquals(2, workService.getById(id).getStatus(), "应变为待审核");
    }

    @Test
    @Order(4)
    @DisplayName("撤回 - 待审核作品撤回为草稿")
    void shouldWithdraw() {
        Long id = newDraft("撤回作品");
        addChapter(id);
        authorController.submit(authA, id);
        authorController.withdraw(authA, id);
        assertEquals(0, workService.getById(id).getStatus());
    }

    @Test
    @Order(5)
    @DisplayName("越权防护 - 作者B不能编辑作者A的作品")
    void shouldForbidCrossAuthorEdit() {
        Long id = newDraft("A的作品");
        WorkSaveDTO bDto = dto("被篡改", "novel");
        bDto.setId(id);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authorController.update(authB, bDto));
        assertEquals(403, ex.getCode());
    }

    @Test
    @Order(6)
    @DisplayName("驳回后重新提交 - 已驳回作品可再次提交")
    void shouldResubmitAfterReject() {
        Long id = newDraft("驳回重提作品");
        addChapter(id);
        authorController.submit(authA, id);
        // 模拟管理员驳回
        workService.lambdaUpdate().eq(Work::getId, id)
                .set(Work::getStatus, 3).set(Work::getRejectReason, "内容不完整").update();
        authorController.submit(authA, id);
        assertEquals(2, workService.getById(id).getStatus());
        assertNull(workService.getById(id).getRejectReason(), "重新提交后应清空驳回理由");
    }

    @Test
    @Order(7)
    @DisplayName("下架 - 已上架作品可下架为草稿")
    void shouldOfflinePublishedWork() {
        Long id = newDraft("下架作品");
        workService.lambdaUpdate().eq(Work::getId, id).set(Work::getStatus, 1).update();
        authorController.offline(authA, id);
        assertEquals(0, workService.getById(id).getStatus());
    }

    @Test
    @Order(8)
    @DisplayName("公开可见 - 审核通过后进入公开列表，草稿不可见")
    void shouldAppearInPublicListAfterApproval() {
        Long publicId = newDraft("过审作品");
        Long draftId = newDraft("未过审作品");
        workService.lambdaUpdate().eq(Work::getId, publicId).set(Work::getStatus, 1).update();

        Page<Work> page = workService.pageByType("manga", 1, 10, null);
        assertEquals(1, page.getTotal(), "仅过审作品可见");
        assertEquals("过审作品", page.getRecords().get(0).getTitle());
    }
}
