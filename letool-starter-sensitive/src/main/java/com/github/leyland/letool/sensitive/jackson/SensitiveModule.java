package com.github.leyland.letool.sensitive.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson 脱敏模块 —— 注册后，序列化时自动对 @Sensitive 字段脱敏.
 */
public class SensitiveModule extends SimpleModule {

    public SensitiveModule() {
        super("letool-sensitive-module");
        addSerializer(String.class, new SensitiveJsonSerializer());
    }
}
