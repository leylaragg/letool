package com.github.leyland.letool.demo.spring.mvc.config.handler;

import com.github.leyland.letool.demo.spring.mvc.pojo.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.theme.ThemeChangeInterceptor;

/**
 * @ClassName <h2>WebConfig</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
/*@Configuration
@EnableWebMvc */ //支持MVC配置
public class WebConfig implements WebMvcConfigurer {

    /**
     * 添加拦截器，默认执行顺序为添加顺序
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LocaleChangeInterceptor());
        registry.addInterceptor(new ThemeChangeInterceptor()).addPathPatterns("/**").excludePathPatterns("/admin/**");
        /*
         * 添加拦截器
         * 通过addPathPatterns配置拦截器的拦截路径，可以多次调用该方法
         * 通过excludePathPatterns配置拦截器的不拦截路径，可以多次调用该方法
         */
        registry.addInterceptor(new MyInterceptor1()).addPathPatterns("/a/**");
    }

    @Bean("user")
    public User getUser(){
        User user = new User();
        user.setName("Rungo");
        return user;
    }
}
