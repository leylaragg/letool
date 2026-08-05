package com.github.leyland.letool.sensitive.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.core.SensitiveProcessor;
import com.github.leyland.letool.sensitive.exception.SensitiveException;

import java.util.List;

/**
 * 只为带脱敏注解的字符串属性安装字段级序列化器。
 *
 * <p>普通字符串属性继续使用用户配置的 Jackson 序列化器，避免 Starter
 * 通过全局 {@code String} 序列化器改变应用既有行为。</p>
 */
final class SensitiveBeanSerializerModifier extends BeanSerializerModifier {

    private final SensitiveProcessor processor;

    /**
     * 创建字段序列化器修改器。
     *
     * @param processor 脱敏处理器
     */
    SensitiveBeanSerializerModifier(SensitiveProcessor processor) {
        this.processor = processor;
    }

    /**
     * 为带 {@link Sensitive} 注解的字符串属性安装脱敏序列化器。
     *
     * @param config Jackson 序列化配置
     * @param beanDescription 当前对象描述
     * @param beanProperties 当前对象的属性写入器
     * @return 已完成字段级增强的属性写入器列表
     */
    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDescription,
            List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            Sensitive annotation = writer.getAnnotation(Sensitive.class);
            if (annotation == null) {
                continue;
            }
            validateStringProperty(beanDescription.getBeanClass(), writer.getName(), writer.getType());
            writer.assignSerializer(new SensitiveJsonSerializer(processor, annotation));
        }
        return beanProperties;
    }

    /**
     * 校验脱敏注解只能用于字符串属性。
     *
     * @param beanType 属性所属类型
     * @param propertyName 属性名称
     * @param propertyType 属性类型
     */
    private static void validateStringProperty(
            Class<?> beanType,
            String propertyName,
            JavaType propertyType) {
        if (!String.class.equals(propertyType.getRawClass())) {
            throw SensitiveException.configurationInvalid(
                    beanType.getName() + "." + propertyName + " 必须是 String 类型");
        }
    }
}
