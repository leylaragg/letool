package com.github.leyland.letool.demo.spring.mvc.config.fromatter;

import org.springframework.format.FormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @ClassName <h2>CustomFormatterRegistrar</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class CustomFormatterRegistrar implements FormatterRegistrar {

    @Resource
    private PersonAnnotationFormatterFactory personAnnotationFormatterFactory;
    @Resource
    private SexAnnotationFormatterFactory sexAnnotationFormatterFactory;

    @Override
    public void registerFormatters(FormatterRegistry registry) {
        registry.addFormatterForFieldAnnotation(personAnnotationFormatterFactory);
        registry.addFormatterForFieldAnnotation(sexAnnotationFormatterFactory);
    }
}
