package com.github.leyland.letool.thread.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncWithContext {

    String value() default "task-executor";
}
