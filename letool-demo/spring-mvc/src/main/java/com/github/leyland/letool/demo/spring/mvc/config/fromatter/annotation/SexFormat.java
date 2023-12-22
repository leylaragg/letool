package com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation;

import java.lang.annotation.*;

/**
 * @ClassName <h2>SexFormat</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface SexFormat {

    String value() default "";

}
