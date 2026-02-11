package com.getoffer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 配置（前后端分离开发联调）。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Last-Event-ID")
                .maxAge(3600);
    }
}
