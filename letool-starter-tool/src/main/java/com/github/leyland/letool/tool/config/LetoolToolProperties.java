package com.github.leyland.letool.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the base tool starter.
 */
@ConfigurationProperties(prefix = "letool.tool")
public class LetoolToolProperties {

    private final Redis redis = new Redis();

    public Redis getRedis() {
        return redis;
    }

    public static class Redis {
        /**
         * Package prefixes accepted by Fastjson2 auto type when the starter creates
         * the default RedisTemplate. Add application package prefixes here, for
         * example {@code com.example}.
         */
        private List<String> autoTypeAcceptPrefixes = new ArrayList<>(
                List.of("org.springframework", "com.github.leyland")
        );

        public List<String> getAutoTypeAcceptPrefixes() {
            return autoTypeAcceptPrefixes;
        }

        public void setAutoTypeAcceptPrefixes(List<String> autoTypeAcceptPrefixes) {
            this.autoTypeAcceptPrefixes = autoTypeAcceptPrefixes == null
                    ? new ArrayList<>()
                    : autoTypeAcceptPrefixes;
        }
    }
}
