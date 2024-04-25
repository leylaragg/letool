package com.github.leyland.letool.demo.basic.core.anno;

import java.lang.annotation.*;


@Target({ ElementType.FIELD })
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeMapper {

    /**
     * 数据库字段，如果为 "" 则不进行处理 -- 一般只需要此属性即可
     * @return
     */
    String value();

    /**
     * JavaBean成员变量别名   --  用于Map格式化对象
     * @return
     */
    String properties() default "";


    /**
     * 特殊类型     --  用于Map格式化对象
     * @return
     */
    Class<?> type() default void.class;

    /**
     * 日期类型格式化
     * @return
     */
    String dateFormat() default "";


    /**
     * 字段描述
     * @return
     */
    String description() default "";



}
