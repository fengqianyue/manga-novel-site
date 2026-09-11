package com.manganovel.config;

import com.manganovel.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Value("${app.upload-path:uploads/}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 优先使用绝对路径配置，否则基于当前目录解析（注意：不同部署方式可能导致路径不一致）
        File uploadDir = new File(uploadPath);
        String absolutePath = uploadDir.isAbsolute() ? uploadPath : new File(uploadPath).getAbsolutePath();
        if (!absolutePath.endsWith(File.separator)) {
            absolutePath += File.separator;
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/login", "/api/user/admin-login", "/api/user/register",
                        "/api/work/list",
                        "/api/chapter/list/**",
                        "/api/tag/list",
                        "/api/manga-page/list/**",
                        "/api/favorite/**",
                        "/api/reading-progress/**",
                        "/api/comment/list/**",
                        "/api/file/upload/avatar");
    }
}
