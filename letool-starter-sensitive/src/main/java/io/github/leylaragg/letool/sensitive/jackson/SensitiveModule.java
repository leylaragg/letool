package io.github.leylaragg.letool.sensitive.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.leylaragg.letool.sensitive.core.SensitiveProcessor;
import io.github.leylaragg.letool.sensitive.core.SensitiveStrategyRegistry;

import java.io.Serial;

/**
 * 为带脱敏注解的字符串属性提供字段级处理的 Jackson 模块。
 *
 * <p>该模块不会注册全局字符串序列化器，因此不会覆盖用户自己的字符串格式方案。</p>
 */
public final class SensitiveModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 使用内置策略创建 Jackson 脱敏模块。
     */
    public SensitiveModule() {
        this(new SensitiveProcessor(SensitiveStrategyRegistry.defaults()));
    }

    /**
     * 使用指定处理器创建 Jackson 脱敏模块。
     *
     * @param processor 脱敏处理器
     */
    public SensitiveModule(SensitiveProcessor processor) {
        super("letool-sensitive-module");
        if (processor == null) {
            throw io.github.leylaragg.letool.sensitive.exception.SensitiveException
                    .configurationInvalid("脱敏处理器不能为空");
        }
        setSerializerModifier(new SensitiveBeanSerializerModifier(processor));
    }
}
