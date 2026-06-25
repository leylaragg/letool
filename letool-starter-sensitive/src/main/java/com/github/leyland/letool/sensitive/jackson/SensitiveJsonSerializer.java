package com.github.leyland.letool.sensitive.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.core.SensitiveProcessor;

import java.io.IOException;

/**
 * Jackson 脱敏序列化器 —— 在 JSON 序列化时对标注 @Sensitive 的 String 字段自动脱敏.
 *
 * <h3>工作原理</h3>
 * <pre>
 *   1. SensitiveModule 将此序列化器注册到 ObjectMapper，匹配 String 类型
 *   2. Jackson 序列化每个 String 字段时，调用 createContextual()
 *   3. createContextual 检查字段上是否有 @Sensitive 注解
 *      → 有：创建携带注解实例的 SensitiveJsonSerializer
 *      → 无：返回 this（无操作序列化器）
 *   4. serialize() 调用 SensitiveProcessor.mask() 完成脱敏
 * </pre>
 *
 * <h3>ContextualSerializer 的作用</h3>
 * <p>ContextualSerializer 让序列化器能感知当前正在序列化的是哪个字段——
 * 从而读取字段上的 @Sensitive 注解。普通的 JsonSerializer 只能拿到值，不知道字段上下文。</p>
 */
public class SensitiveJsonSerializer extends JsonSerializer<String> implements ContextualSerializer {

    /**
     * 当前字段上的 @Sensitive 注解 —— null 表示该字段不需要脱敏。
     * createContextual() 会为每个字段创建独立的序列化器实例，携带该字段的注解。
     */
    private Sensitive annotation;

    public SensitiveJsonSerializer() {}

    /** 私有构造器 —— 为特定字段创建附带注解的序列化器实例 */
    private SensitiveJsonSerializer(Sensitive annotation) {
        this.annotation = annotation;
    }

    /**
     * 实际序列化 —— 调用 SensitiveProcessor 完成脱敏，将结果写入 JSON 输出流。
     * annotation 为 null 时原样输出（即没有 @Sensitive 的普通 String 字段）。
     */
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || annotation == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(SensitiveProcessor.mask(value, annotation));
    }

    /**
     * ContextualSerializer 回调 —— Jackson 在序列化每个字段前调用此方法，
     * 检查该字段是否有 @Sensitive 注解，有则返回一个新的携带注解的序列化器实例。
     *
     * @param property 当前正在序列化的字段的元数据（可获取该字段上的注解）
     * @return 带注解的序列化器（有 @Sensitive）或 this（无 @Sensitive，直接写原文）
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property != null) {
            Sensitive ann = property.getAnnotation(Sensitive.class);
            if (ann != null) {
                // 创建携带注解的实例 → 序列化时会调用 SensitiveProcessor.mask()
                return new SensitiveJsonSerializer(ann);
            }
        }
        // 无 @Sensitive → 返回自身（serialize 方法中 annotation==null，原样输出）
        return this;
    }
}
