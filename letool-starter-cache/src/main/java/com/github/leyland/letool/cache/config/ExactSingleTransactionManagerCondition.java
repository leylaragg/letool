package com.github.leyland.letool.cache.config;

import org.springframework.transaction.PlatformTransactionManager;

/** 仅存在一个事务管理器时才允许创建默认缓存协调器。 */
final class ExactSingleTransactionManagerCondition extends ExactSingleBeanCondition {

    ExactSingleTransactionManagerCondition() {
        super(PlatformTransactionManager.class);
    }
}
