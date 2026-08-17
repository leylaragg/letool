package io.github.leylaragg.letool.sensitive.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.github.leylaragg.letool.sensitive.annotation.Sensitive;
import io.github.leylaragg.letool.sensitive.core.SensitiveProcessor;

import java.io.IOException;

/**
 * 仅用于带 {@link Sensitive} 注解字段的 Jackson 字符串序列化器。
 */
public final class SensitiveJsonSerializer extends JsonSerializer<Object> {

    private final SensitiveProcessor processor;
    private final Sensitive annotation;

    /**
     * 创建字段级脱敏序列化器。
     *
     * @param processor 脱敏处理器
     * @param annotation 当前字段的脱敏注解
     */
    SensitiveJsonSerializer(SensitiveProcessor processor, Sensitive annotation) {
        this.processor = processor;
        this.annotation = annotation;
    }

    /**
     * 对字符串字段执行脱敏并写入 JSON。
     *
     * @param value 字段原始值
     * @param generator JSON 输出器
     * @param provider 序列化上下文
     * @throws IOException 写入 JSON 失败时抛出
     */
    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        if (!(value instanceof String stringValue)) {
            throw new IOException("@Sensitive 只能标注 String 类型字段");
        }
        generator.writeString(processor.mask(stringValue, annotation));
    }
}
