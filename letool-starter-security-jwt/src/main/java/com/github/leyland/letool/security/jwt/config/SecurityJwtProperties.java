package com.github.leyland.letool.security.jwt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security JWT 配置属性
 *
 * @author Rungo
 */
@ConfigurationProperties(prefix = "letool.security.jwt")
public class SecurityJwtProperties {

    /**
     * 是否启用安全模块
     */
    private boolean enabled = true;

    /**
     * 认证模式: security (Spring Security) 或 token (纯Token模式)
     */
    private AuthMode mode = AuthMode.TOKEN;

    /**
     * JWT 配置
     */
    private JwtConfig jwt = new JwtConfig();

    /**
     * Token 存储配置
     */
    private TokenStorageConfig storage = new TokenStorageConfig();

    /**
     * 白名单路径配置
     */
    private String[] whitePaths = new String[]{"/login", "/logout", "/error"};

    /**
     * 是否启用权限注解
     */
    private boolean enablePermission = true;

    /**
     * 是否启用角色注解
     */
    private boolean enableRole = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AuthMode getMode() {
        return mode;
    }

    public void setMode(AuthMode mode) {
        this.mode = mode;
    }

    public JwtConfig getJwt() {
        return jwt;
    }

    public void setJwt(JwtConfig jwt) {
        this.jwt = jwt;
    }

    public TokenStorageConfig getStorage() {
        return storage;
    }

    public void setStorage(TokenStorageConfig storage) {
        this.storage = storage;
    }

    public String[] getWhitePaths() {
        return whitePaths;
    }

    public void setWhitePaths(String[] whitePaths) {
        this.whitePaths = whitePaths;
    }

    public boolean isEnablePermission() {
        return enablePermission;
    }

    public void setEnablePermission(boolean enablePermission) {
        this.enablePermission = enablePermission;
    }

    public boolean isEnableRole() {
        return enableRole;
    }

    public void setEnableRole(boolean enableRole) {
        this.enableRole = enableRole;
    }

    /**
     * 认证模式枚举
     */
    public enum AuthMode {
        /**
         * Spring Security 模式 - 使用完整的 Spring Security 框架
         */
        SECURITY,
        /**
         * 纯 Token 模式 - 不依赖 Spring Security，仅使用 Token 拦截器
         */
        TOKEN
    }

    /**
     * JWT 配置
     */
    public static class JwtConfig {
        /**
         * 密钥 (至少256位用于HS256)
         */
        private String secret = "letool-default-secret-key-please-change-in-production-environment";

        /**
         * Access Token 过期时间(秒)
         */
        private long accessTokenExpiration = 7200;

        /**
         * Refresh Token 过期时间(秒)
         */
        private long refreshTokenExpiration = 604800;

        /**
         * Token 前缀
         */
        private String tokenPrefix = "Bearer ";

        /**
         * Token 请求头名称
         */
        private String headerName = "Authorization";

        /**
         * JWT 签名算法
         */
        private String algorithm = "HS256";

        /**
         * Issuer (签发者)
         */
        private String issuer = "letool";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getAccessTokenExpiration() {
            return accessTokenExpiration;
        }

        public void setAccessTokenExpiration(long accessTokenExpiration) {
            this.accessTokenExpiration = accessTokenExpiration;
        }

        public long getRefreshTokenExpiration() {
            return refreshTokenExpiration;
        }

        public void setRefreshTokenExpiration(long refreshTokenExpiration) {
            this.refreshTokenExpiration = refreshTokenExpiration;
        }

        public String getTokenPrefix() {
            return tokenPrefix;
        }

        public void setTokenPrefix(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    /**
     * Token 存储配置
     */
    public static class TokenStorageConfig {
        /**
         * 存储类型: memory, redis, none
         */
        private StorageType type = StorageType.MEMORY;

        /**
         * 是否启用 Token 缓存 (用于校验和踢出用户)
         */
        private boolean enableCache = true;

        /**
         * Redis key 前缀 (仅当 type=redis 时生效)
         */
        private String redisPrefix = "letool:token:";

        public StorageType getType() {
            return type;
        }

        public void setType(StorageType type) {
            this.type = type;
        }

        public boolean isEnableCache() {
            return enableCache;
        }

        public void setEnableCache(boolean enableCache) {
            this.enableCache = enableCache;
        }

        public String getRedisPrefix() {
            return redisPrefix;
        }

        public void setRedisPrefix(String redisPrefix) {
            this.redisPrefix = redisPrefix;
        }

        /**
         * 存储类型枚举
         */
        public enum StorageType {
            /**
             * 内存存储 (默认)
             */
            MEMORY,
            /**
             * Redis 存储 (需要引入 redis 依赖)
             */
            REDIS,
            /**
             * 不存储 (仅验证 Token 签名)
             */
            NONE
        }
    }
}