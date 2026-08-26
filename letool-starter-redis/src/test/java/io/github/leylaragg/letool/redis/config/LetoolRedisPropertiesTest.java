package io.github.leylaragg.letool.redis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Starter 配置默认值与公开元数据契约测试。
 */
class LetoolRedisPropertiesTest {

    /** 元数据必须完整公开业务会直接配置的九个 Redis 能力开关和策略。 */
    @Test
    void shouldPublishRedisConfigurationMetadata() throws Exception {
        Set<String> expectedNames = Set.of(
                "letool.redis.serialization.auto-type-accept-prefixes",
                "letool.redis.lock.key-prefix",
                "letool.redis.lock.fair",
                "letool.redis.idempotent.key-prefix",
                "letool.redis.cache.cache-null",
                "letool.redis.cache.null-ttl",
                "letool.redis.cache.ttl-jitter",
                "letool.redis.cache.lock-wait",
                "letool.redis.cache.lock-key-prefix");

        try (InputStream input = getClass().getResourceAsStream(
                "/META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();
            JSONObject root = new JSONObject(new String(
                    input.readAllBytes(), StandardCharsets.UTF_8));
            JSONArray properties = root.getJSONArray("properties");
            Set<String> actualNames = new HashSet<>();
            for (int index = 0; index < properties.length(); index++) {
                JSONObject metadata = properties.getJSONObject(index);
                assertThat(actualNames.add(metadata.getString("name"))).isTrue();
                assertThat(metadata.getString("description")).isNotBlank();
            }
            assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames);
        }
    }
}
