package org.aiknowledgebase.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 延长 StreamingResponseBody 的异步请求超时时间。
     * 这里设置为 5 分钟，避免联网搜索 + 大模型生成时被提前中断。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(300_000);
    }
}