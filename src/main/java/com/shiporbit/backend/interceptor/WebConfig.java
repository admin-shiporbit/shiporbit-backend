package com.shiporbit.backend.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Request/response logging moved to logging.TraceFilter (runs before security, carries trace_id + steps).
        // RequestInterceptor is no longer registered; it duplicated the "-> status (ms)" line.
    }
}
