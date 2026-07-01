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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public AuthMode getAuthMode() { return authMode; }
    public void setAuthMode(AuthMode authMode) { this.authMode = authMode; }
    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public List<String> getExcludePaths() { return excludePaths; }
    public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }

    /**
     * JWT 令牌配置。
     */
    public static class Jwt {
        /** 签名密钥（HMAC-SHA256），生产环境必须通过环境变量覆盖 */
        private String secret = "letool-default-secret-change-in-production";

        /** AccessToken 有效期（秒），默认 1800（30 分钟） */
        private long accessTokenExpiration = 1800;

        /** RefreshToken 有效期（秒），默认 604800（7 天） */
        private long refreshTokenExpiration = 604800;

        /** JWT 签发者标识 */
        private String issuer = "letool";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getAccessTokenExpiration() { return accessTokenExpiration; }
        public void setAccessTokenExpiration(long accessTokenExpiration) { this.accessTokenExpiration = accessTokenExpiration; }
        public long getRefreshTokenExpiration() { return refreshTokenExpiration; }
        public void setRefreshTokenExpiration(long refreshTokenExpiration) { this.refreshTokenExpiration = refreshTokenExpiration; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
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

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public String getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }
        public String getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(String allowedHeaders) { this.allowedHeaders = allowedHeaders; }
        public long getMaxAge() { return maxAge; }
        public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
    }
}
