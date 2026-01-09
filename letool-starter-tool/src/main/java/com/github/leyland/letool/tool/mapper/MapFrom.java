package com.github.leyland.letool.tool.mapper;

import java.lang.annotation.*;

/**
 * 字段映射注解
 * 用于标记 VO 字段从源对象的哪个属性获取值
 *
 * @author leyland
 * @date 2025-01-08
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapFrom {

    /**
     * 源对象在参数列表中的索引
     * 如果有多个源对象，通过索引指定使用哪一个
     * 默认为0，表示第一个源对象
     */
    int sourceIndex() default 0;

    /**
     * 源对象中的属性路径
     * 支持嵌套属性，例如：user.address.city
     * 如果不指定，则使用字段名进行匹配
     */
    String value() default "";

    /**
     * 是否忽略大小写
     * 默认为false，区分大小写
     */
    boolean ignoreCase() default false;

    /**
     * 是否忽略空值
     * 如果为true，当源字段为null时不会覆盖目标字段
     */
    boolean ignoreNull() default false;

    /**
     * 自定义转换器
     * 用于特殊类型的转换逻辑
     */
    Class<? extends TypeConverter> converter() default TypeConverter.class;
}
