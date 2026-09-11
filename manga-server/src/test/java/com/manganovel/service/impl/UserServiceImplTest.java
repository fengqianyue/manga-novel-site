package com.manganovel.service.impl;

import com.manganovel.common.BusinessException;
import com.manganovel.dto.LoginRequest;
import com.manganovel.dto.RegisterRequest;
import com.manganovel.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 单元测试
 * 覆盖：注册、登录、密码加密、唯一性校验、异常场景
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceImplTest {

    @Autowired
    private UserServiceImpl userService;

    private static final String TEST_USER = "testuser";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";

    @Autowired
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        // @TableLogic 会拦截所有 delete，必须用原生 SQL 做物理删除
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM user");
        }
    }

    // ==================== 注册测试 ====================

    @Test
    @Order(1)
    @DisplayName("注册 - 正常注册成功")
    void shouldRegisterSuccessfully() {
        RegisterRequest req = buildRegister(TEST_USER, TEST_EMAIL, TEST_PASSWORD);
        assertDoesNotThrow(() -> userService.register(req));

        // 验证数据已入库
        User user = userService.lambdaQuery().eq(User::getUsername, TEST_USER).one();
        assertNotNull(user);
        assertEquals(TEST_USER, user.getUsername());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertEquals(0, user.getRole(), "新用户默认 role=0");
        assertNotEquals(TEST_PASSWORD, user.getPassword(), "密码应为 BCrypt 加密");
    }

    @Test
    @Order(2)
    @DisplayName("注册 - 用户名重复应抛异常")
    void shouldRejectDuplicateUsername() {
        // 先注册一个
        userService.register(buildRegister(TEST_USER, "a@test.com", TEST_PASSWORD));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.register(buildRegister(TEST_USER, "b@test.com", TEST_PASSWORD))
        );
        assertTrue(ex.getMessage().contains("用户名"));
    }

    @Test
    @Order(3)
    @DisplayName("注册 - 邮箱重复应抛异常")
    void shouldRejectDuplicateEmail() {
        userService.register(buildRegister("user1", TEST_EMAIL, TEST_PASSWORD));

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.register(buildRegister("user2", TEST_EMAIL, TEST_PASSWORD))
        );
        assertTrue(ex.getMessage().contains("邮箱"));
    }

    // ==================== 登录测试 ====================

    @Test
    @Order(4)
    @DisplayName("登录 - 正常登录成功，返回不含密码的 User")
    void shouldLoginSuccessfully() {
        userService.register(buildRegister(TEST_USER, TEST_EMAIL, TEST_PASSWORD));

        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USER);
        req.setPassword(TEST_PASSWORD);

        User user = userService.login(req);
        assertNotNull(user);
        assertEquals(TEST_USER, user.getUsername());
        assertNull(user.getPassword(), "登录返回的 User 不应包含密码");
    }

    @Test
    @Order(5)
    @DisplayName("登录 - 用户不存在抛异常")
    void shouldRejectNonExistentUser() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("any");

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.login(req)
        );
        assertTrue(ex.getMessage().contains("用户不存在"));
    }

    @Test
    @Order(6)
    @DisplayName("登录 - 密码错误抛异常")
    void shouldRejectWrongPassword() {
        userService.register(buildRegister(TEST_USER, TEST_EMAIL, TEST_PASSWORD));

        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USER);
        req.setPassword("wrongpassword");

        BusinessException ex = assertThrows(BusinessException.class, () ->
            userService.login(req)
        );
        assertTrue(ex.getMessage().contains("密码错误"));
    }

    // ==================== 辅助方法 ====================

    private RegisterRequest buildRegister(String username, String email, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
