package com.github.leyland.letool.demo.spring.mvc.config.fromatter;

import com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation.SexFormat;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * @ClassName <h2>SexAnnotationFormatterFactory</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class SexAnnotationFormatterFactory implements AnnotationFormatterFactory<SexFormat> {

    Set<Class<?>> classSet = Collections.singleton(String.class);

    @Override
    public Set<Class<?>> getFieldTypes() {
        return classSet;
    }

    @Override
    public Parser<?> getParser(SexFormat annotation, Class<?> fieldType) {
        return configureFormatterFrom(annotation);
    }

    @Override
    public Printer<?> getPrinter(SexFormat annotation, Class<?> fieldType) {
        return configureFormatterFrom(annotation);
    }

    private Formatter<String> configureFormatterFrom(SexFormat annotation) {
        String value = annotation.value();
        if ("0".equals(value)) {
            return new SexFormatter.ManFormatter();
        }
        if ("1".equals(value)) {
            return new SexFormatter.WomanFormatter();
        }
        return new SexFormatter();
    }

}
