package com.github.leyland.letool.demo.spring.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * @ClassName <h2>SpringWebConfig</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Configuration
public class SpringWebConfig {

    @Bean
    public MethodValidationPostProcessor validationPostProcessor() {
        return new MethodValidationPostProcessor();
    }
}
