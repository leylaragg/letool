package com.github.leyland.letool.security.config;

import com.github.leyland.letool.security.enums.AuthMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全模块配置属性，前缀 {@code letool.security}。
 *
 * <p>涵盖认证模式、JWT 参数、排除路径和跨域配置。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.security")
public class SecurityProperties {

    /** 是否启用安全模块，默认 true */
    private boolean enabled = true;

    /** 认证模式，默认 JWT 无状态模式 */
    private AuthMode authMode = AuthMode.JWT;

    /** JWT 相关配置 */
    private Jwt jwt = new Jwt();

    /** 不经过安全过滤的路径列表（支持 Ant 风格通配符） */
    private List<String> excludePaths = new ArrayList<>();

    /** 跨域配置 */
    private Cors cors = new Cors();

    /**
     * 判断是否启用安全模块。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用安全模块。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取认证模式。
     *
     * @return 非空认证模式
     */
    public AuthMode getAuthMode() {
        return authMode;
    }

    /**
     * 设置认证模式。
     *
     * @param authMode 认证模式；传入 {@code null} 时使用 JWT
     */
    public void setAuthMode(AuthMode authMode) {
        this.authMode = authMode == null ? AuthMode.JWT : authMode;
    }

    /**
     * 获取 JWT 配置。
     *
     * @return 非空 JWT 配置
     */
    public Jwt getJwt() {
        return jwt;
    }

    /**
     * 设置 JWT 配置。
     *
     * @param jwt JWT 配置；传入 {@code null} 时恢复默认对象
     */
    public void setJwt(Jwt jwt) {
        this.jwt = jwt == null ? new Jwt() : jwt;
    }

    /**
     * 获取公开路径列表。
     *
     * @return 当前路径列表
     */
    public List<String> getExcludePaths() {
        return excludePaths;
    }

    /**
     * 设置公开路径列表。
     *
     * @param excludePaths 路径列表；传入 {@code null} 时规范为空列表
     */
    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null
                ? new ArrayList<>()
                : new ArrayList<>(excludePaths);
    }

    /**
     * 获取跨域配置。
     *
     * @return 非空跨域配置
     */
    public Cors getCors() {
        return cors;
    }

    /**
     * 设置跨域配置。
     *
     * @param cors 跨域配置；传入 {@code null} 时恢复默认对象
     */
    public void setCors(Cors cors) {
        this.cors = cors == null ? new Cors() : cors;
    }

    /**
     * JWT 令牌配置。
     */
    public static class Jwt {
        /** 签名密钥（HMAC-SHA256），生产环境必须通过环境变量覆盖 */
        private String secret;

        /** AccessToken 有效期（秒），默认 1800（30 分钟） */
        private long accessTokenExpiration = 1800;

        /** RefreshToken 有效期（秒），默认 604800（7 天） */
        private long refreshTokenExpiration = 604800;

        /** JWT 签发者标识 */
        private String issuer = "letool";

        /**
         * 获取 HMAC 签名密钥。
         *
         * @return 密钥文本
         */
        public String getSecret() {
            return secret;
        }

        /**
         * 设置 HMAC 签名密钥。
         *
         * @param secret UTF-8 长度至少 32 字节的密钥文本
         */
        public void setSecret(String secret) {
            this.secret = secret;
        }

        /**
         * 获取 AccessToken 有效期。
         *
         * @return 有效期秒数
         */
        public long getAccessTokenExpiration() {
            return accessTokenExpiration;
        }

        /**
         * 设置 AccessToken 有效期。
         *
         * @param accessTokenExpiration 有效期秒数
         */
        public void setAccessTokenExpiration(long accessTokenExpiration) {
            this.accessTokenExpiration = accessTokenExpiration;
        }

        /**
         * 获取 RefreshToken 有效期。
         *
         * @return 有效期秒数
         */
        public long getRefreshTokenExpiration() {
            return refreshTokenExpiration;
        }

        /**
         * 设置 RefreshToken 有效期。
         *
         * @param refreshTokenExpiration 有效期秒数
         */
        public void setRefreshTokenExpiration(long refreshTokenExpiration) {
            this.refreshTokenExpiration = refreshTokenExpiration;
        }

        /**
         * 获取 JWT 签发者。
         *
         * @return 签发者标识
         */
        public String getIssuer() {
            return issuer;
        }

        /**
         * 设置 JWT 签发者。
         *
         * @param issuer 签发者标识
         */
        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    /**
     * 跨域（CORS）配置。
     */
    public static class Cors {
        /** 是否启用跨域支持，默认 true */
        private boolean enabled = true;

        /** 允许的源，多个用逗号分隔，默认 * */
        private String allowedOrigins = "*";

        /** 允许的 HTTP 方法，多个用逗号分隔 */
        private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";

        /** 允许的请求头，多个用逗号分隔，默认 * */
        private String allowedHeaders = "*";

        /** 预检请求缓存时间（秒），默认 3600 */
        private long maxAge = 3600;

        /** 是否允许跨域请求携带 Cookie 等凭据，默认 {@code false} */
        private boolean allowCredentials = false;

        /**
         * 判断是否启用跨域支持。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用跨域支持。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取允许的来源配置。
         *
         * @return 逗号分隔的来源
         */
        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        /**
         * 设置允许的来源。
         *
         * @param allowedOrigins 逗号分隔的来源
         */
        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        /**
         * 获取允许的 HTTP 方法。
         *
         * @return 逗号分隔的方法
         */
        public String getAllowedMethods() {
            return allowedMethods;
        }

        /**
         * 设置允许的 HTTP 方法。
         *
         * @param allowedMethods 逗号分隔的方法
         */
        public void setAllowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        /**
         * 获取允许的请求头。
         *
         * @return 逗号分隔的请求头
         */
        public String getAllowedHeaders() {
            return allowedHeaders;
        }

        /**
         * 设置允许的请求头。
         *
         * @param allowedHeaders 逗号分隔的请求头
         */
        public void setAllowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        /**
         * 获取预检请求缓存时间。
         *
         * @return 缓存秒数
         */
        public long getMaxAge() {
            return maxAge;
        }

        /**
         * 设置预检请求缓存时间。
         *
         * @param maxAge 缓存秒数
         */
        public void setMaxAge(long maxAge) {
            this.maxAge = maxAge;
        }

        /**
         * 判断跨域请求是否允许携带凭据。
         *
         * @return 允许携带凭据时返回 {@code true}
         */
        public boolean isAllowCredentials() { return allowCredentials; }

        /**
         * 设置跨域请求是否允许携带凭据。
         *
         * @param allowCredentials 是否允许携带凭据
         */
        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }
}
