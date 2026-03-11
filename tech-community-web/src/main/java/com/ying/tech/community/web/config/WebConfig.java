package com.ying.tech.community.web.config;

import com.ying.tech.community.web.hook.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/user/**") // 拦截 /user/ 下的所有请求
                .addPathPatterns("/article/**") // 以后有了文章接口也要拦截
                // 👇 排除白名单：登录和注册接口不需要 Token
                .excludePathPatterns("/user/login", "/user/register");
    }

    // 之前配的静态资源映射保留着就行
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
