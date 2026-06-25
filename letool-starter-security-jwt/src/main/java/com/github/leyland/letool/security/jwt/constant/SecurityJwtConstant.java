package com.github.leyland.letool.security.jwt.constant;

/**
 * Security JWT 常量定义
 *
 * @author Rungo
 */
public final class SecurityJwtConstant {

    /**
     * Token 类型 - Access Token
     */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /**
     * Token 类型 - Refresh Token
     */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /**
     * 用户ID Claim Key
     */
    public static final String CLAIM_USER_ID = "userId";

    /**
     * 用户名 Claim Key
     */
    public static final String CLAIM_USERNAME = "username";

    /**
     * 角色 Claim Key
     */
    public static final String CLAIM_ROLES = "roles";

    /**
     * 权限 Claim Key
     */
    public static final String CLAIM_PERMISSIONS = "permissions";

    /**
     * Token ID Claim Key
     */
    public static final String CLAIM_TOKEN_ID = "tokenId";

    /**
     * Token 类型 Claim Key
     */
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    /**
     * 用户状态 Claim Key
     */
    public static final String CLAIM_STATUS = "status";

    /**
     * 默认状态 - 正常
     */
    public static final int STATUS_NORMAL = 1;

    /**
     * 默认状态 - 禁用
     */
    public static final int STATUS_DISABLED = 0;

    /**
     * 默认认证类型前缀
     */
    public static final String DEFAULT_TOKEN_PREFIX = "Bearer ";

    /**
     * 默认请求头名称
     */
    public static final String DEFAULT_HEADER_NAME = "Authorization";

    /**
     * 默认 Issuer
     */
    public static final String DEFAULT_ISSUER = "letool";

    private SecurityJwtConstant() {
    }
}