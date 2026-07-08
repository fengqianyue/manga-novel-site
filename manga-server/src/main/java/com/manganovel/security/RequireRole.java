package com.manganovel.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 允许的角色：0=普通用户 1=管理员 */
    int value() default 1;
}
