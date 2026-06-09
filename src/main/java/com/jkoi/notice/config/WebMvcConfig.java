package com.jkoi.notice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final WxIdInterceptor wxIdInterceptor;

    public WebMvcConfig(WxIdInterceptor wxIdInterceptor) {
        this.wxIdInterceptor = wxIdInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(wxIdInterceptor).addPathPatterns("/**");
    }
}
