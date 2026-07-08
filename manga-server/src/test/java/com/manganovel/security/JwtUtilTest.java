package com.manganovel.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken(1L, "admin", 1);
        assertNotNull(token);

        Long userId = jwtUtil.getUserId(token);
        assertEquals(1L, userId);

        int role = jwtUtil.getRole(token);
        assertEquals(1, role);
    }
}
