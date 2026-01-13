package com.github.leyland.letool.letool.tool.Interceptor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @ClassName <h2>RestTemplateInterceptorConfig</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
@Component
@ConfigurationProperties(prefix = "spring.letool.http.rest-template.interceptor")
public class RestTemplateInterceptorConfig {
    private List<InterceptorRule> rules;

    public List<InterceptorRule> getRules() {
        return rules;
    }

    public void setRules(List<InterceptorRule> rules) {
        this.rules = rules;
    }
}
