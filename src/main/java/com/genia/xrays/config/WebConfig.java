package com.genia.xrays.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Aseguramos que solo bloquee los endpoints reales de IA
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/xrays/evaluate**");
    }
}
