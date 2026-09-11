package com.manganovel.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 允许的最低角色：0=普通用户 1=作者 2=管理员 */
    int value() default 2;
}
