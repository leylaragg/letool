package com.github.leyland.letool.log.audit;

/**
 * 审计操作类型枚举.
 */
public enum AuditType {
    /** 认证相关（登录/登出/修改密码） */
    AUTH,
    /** 管理操作（删除用户/修改配置） */
    ADMIN,
    /** 业务操作（创建订单/审核） */
    BUSINESS,
    /** 系统操作 */
    SYSTEM
}
