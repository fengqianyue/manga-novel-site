package com.manganovel.security;

import com.manganovel.common.BusinessException;
import com.manganovel.entity.Work;
import com.manganovel.service.IWorkService;
import org.springframework.stereotype.Component;

/**
 * 作品属主校验：管理员放行；作者仅可操作自己名下的作品。
 */
@Component
public class WorkOwnerChecker {

    private final JwtUtil jwtUtil;
    private final IWorkService workService;

    public WorkOwnerChecker(JwtUtil jwtUtil, IWorkService workService) {
        this.jwtUtil = jwtUtil;
        this.workService = workService;
    }

    /** 校验当前用户是否有权管理该作品（管理员 或 作者属主），无权抛 403 */
    public void check(Long workId, String auth) {
        String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        Long userId = jwtUtil.getUserId(token);
        int role = jwtUtil.getRole(token);
        Work work = workService.getById(workId);
        if (work == null) throw new BusinessException("作品不存在");
        if (role >= 2) return; // 管理员
        if (role == 1 && userId.equals(work.getUserId())) return; // 作者操作自己的作品
        throw new BusinessException(403, "无权操作该作品");
    }
}
