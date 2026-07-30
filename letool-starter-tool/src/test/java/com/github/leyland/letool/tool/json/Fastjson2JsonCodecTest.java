package com.github.leyland.letool.tool.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Fastjson2 可配置行为和统一 JSON 失败契约。
 */
class Fastjson2JsonCodecTest {

    @Test
    void shouldPreserveLegacyWriterFeaturesByDefault() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.createDefault();
        JsonValue value = new JsonValue();
        value.setName("default");

        String actual = codec.write(value);
        String legacy = JSON.toJSONString(
                value,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.NotWriteDefaultValue
        );

        assertThat(actual).isEqualTo(legacy);
    }

    @Test
    void shouldReplaceDefaultWriterFeaturesThroughBuilder() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.builder()
                .writerFeatures(JSONWriter.Feature.WriteNulls)
                .build();
        JsonValue value = new JsonValue();
        value.setName("custom");

        String actual = codec.write(value);

        assertThat(actual)
                .contains("\"name\":\"custom\"")
                .contains("\"description\":null")
                .contains("\"count\":0");
    }

    @Test
    void shouldApplyDateFormatToStringAndByteSerialization() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.builder()
                .dateFormat("yyyy/MM/dd HH:mm")
                .build();
        LocalDateTime dateTime = LocalDateTime.of(2026, 7, 30, 9, 15);

        assertThat(codec.write(dateTime)).isEqualTo("\"2026/07/30 09:15\"");
        assertThat(codec.writeBytes(dateTime))
                .isEqualTo("\"2026/07/30 09:15\"".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldApplyConfiguredReaderFeatures() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.builder()
                .readerFeatures(JSONReader.Feature.SupportSmartMatch)
                .build();

        SmartMatchValue actual = codec.read("{\"user_name\":\"Leyland\"}", SmartMatchValue.class);

        assertThat(actual.getUserName()).isEqualTo("Leyland");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldRejectUnsafeGlobalAutoTypeFeature() {
        assertThatThrownBy(() -> Fastjson2JsonCodec.builder()
                .readerFeatures(JSONReader.Feature.SupportAutoType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SupportAutoType")
                .hasMessageContaining("allow list");
    }

    @Test
    void shouldDefensivelyCopyWriterFeatureConfiguration() {
        JSONWriter.Feature[] features = {JSONWriter.Feature.WriteNulls};
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.builder()
                .writerFeatures(features)
                .build();
        features[0] = JSONWriter.Feature.NotWriteDefaultValue;

        String actual = codec.write(new JsonValue());

        assertThat(actual)
                .contains("\"description\":null")
                .contains("\"count\":0");
    }

    @Test
    void shouldWrapSerializationFailureWithStableErrorCode() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.builder()
                .writerFeatures(JSONWriter.Feature.ErrorOnNoneSerializable)
                .build();

        assertThatThrownBy(() -> codec.write(new JsonValue()))
                .isInstanceOf(JsonCodecException.class)
                .hasFieldOrPropertyWithValue("code", JsonErrorCode.SERIALIZATION_FAILED.getCode())
                .hasRootCauseInstanceOf(com.alibaba.fastjson2.JSONException.class);
    }

    @Test
    void shouldWrapDeserializationFailureWithoutExposingJsonPayload() {
        Fastjson2JsonCodec codec = Fastjson2JsonCodec.createDefault();
        String invalidJson = "{\"count\":\"sensitive-invalid-number\"}";

        assertThatThrownBy(() -> codec.read(invalidJson, JsonValue.class))
                .isInstanceOf(JsonCodecException.class)
                .hasFieldOrPropertyWithValue("code", JsonErrorCode.DESERIALIZATION_FAILED.getCode())
                .hasMessageNotContaining("sensitive-invalid-number");
    }

    static class JsonValue {
        private String name;
        private String description;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    static class SmartMatchValue {
        private String userName;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

}
