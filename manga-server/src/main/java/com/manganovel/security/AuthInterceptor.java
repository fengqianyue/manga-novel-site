package com.manganovel.security;

import com.manganovel.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequireRole annotation = hm.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return true; // 不需要权限的接口直接放行
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException(401, "请先登录");
        }

        try {
            int userRole = jwtUtil.getRole(token.substring(7));
            if (userRole < annotation.value()) {
                throw new BusinessException(403, "权限不足，需要管理员权限");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        return true;
    }
}
