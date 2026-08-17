package io.github.leylaragg.letool.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础工具 Starter 配置属性。
 */
@ConfigurationProperties(prefix = "letool.tool")
public class LetoolToolProperties {

    private final Redis redis = new Redis();

    /**
     * 返回 Redis 适配器配置。
     *
     * @return 由 Spring Boot 绑定的可变 Redis 配置
     */
    public Redis getRedis() {
        return redis;
    }

    /**
     * Redis 适配器配置。
     */
    public static class Redis {
        /**
         * Starter 创建默认 RedisTemplate 时，Fastjson2 自动类型允许的包名。
         * 应用可在此增加业务包名，例如 {@code com.example}；Letool 会强制补充
         * 末尾包边界。
         */
        private List<String> autoTypeAcceptPrefixes = new ArrayList<>(
                List.of("org.springframework", "io.github.leylaragg")
        );

        /**
         * 返回 Redis 多态反序列化允许的包名。
         *
         * @return 用于配置 Redis 序列化器的可变包名列表
         */
        public List<String> getAutoTypeAcceptPrefixes() {
            return autoTypeAcceptPrefixes;
        }

        /**
         * 替换 Redis 多态反序列化允许的包名。
         *
         * @param autoTypeAcceptPrefixes 包名；传入 {@code null} 时重置为空列表
         */
        public void setAutoTypeAcceptPrefixes(List<String> autoTypeAcceptPrefixes) {
            this.autoTypeAcceptPrefixes = autoTypeAcceptPrefixes == null
                    ? new ArrayList<>()
                    : autoTypeAcceptPrefixes;
        }
    }
}
