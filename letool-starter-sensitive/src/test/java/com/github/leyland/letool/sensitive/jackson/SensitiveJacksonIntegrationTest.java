package com.github.leyland.letool.sensitive.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.core.SensitiveProcessor;
import com.github.leyland.letool.sensitive.core.SensitiveStrategyRegistry;
import com.github.leyland.letool.sensitive.core.SensitiveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson 脱敏模块的真实序列化集成测试。
 */
@DisplayName("Jackson 脱敏集成")
class SensitiveJacksonIntegrationTest {

    /**
     * 验证脱敏模块只接管带注解的字段，并保留用户为普通字符串配置的序列化方案。
     *
     * @throws Exception JSON 序列化或解析失败时抛出
     */
    @Test
    @DisplayName("仅脱敏注解字段且不覆盖用户字符串序列化器")
    void shouldMaskAnnotatedFieldWithoutReplacingUserStringSerializer() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule userModule = new SimpleModule("user-string-module");
        userModule.addSerializer(String.class, new PrefixStringSerializer());
        objectMapper.registerModule(userModule);
        objectMapper.registerModule(new SensitiveModule(
                new SensitiveProcessor(SensitiveStrategyRegistry.defaults())));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new UserView("公开名称", "13812345678")));

        assertThat(json.get("name").asText()).isEqualTo("用户配置:公开名称");
        assertThat(json.get("phone").asText()).isEqualTo("138****5678");
    }

    /**
     * 测试使用的用户字符串序列化器。
     */
    private static final class PrefixStringSerializer extends StdScalarSerializer<String> {

        private PrefixStringSerializer() {
            super(String.class);
        }

        /**
         * 为普通字符串增加可识别前缀。
         *
         * @param value 原始字符串
         * @param generator JSON 输出器
         * @param provider 序列化上下文
         * @throws IOException 写入 JSON 失败时抛出
         */
        @Override
        public void serialize(String value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString("用户配置:" + value);
        }
    }

    /**
     * 测试使用的响应对象。
     */
    private static final class UserView {

        private final String name;

        @Sensitive(type = SensitiveType.PHONE)
        private final String phone;

        /**
         * 创建测试响应对象。
         *
         * @param name 普通字符串字段
         * @param phone 需要脱敏的手机号字段
         */
        private UserView(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }

        /**
         * 获取普通字符串字段。
         *
         * @return 普通字符串字段
         */
        public String getName() {
            return name;
        }

        /**
         * 获取手机号字段。
         *
         * @return 手机号字段
         */
        public String getPhone() {
            return phone;
        }
    }
}
