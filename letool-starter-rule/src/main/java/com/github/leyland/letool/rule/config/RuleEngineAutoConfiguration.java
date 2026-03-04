package com.github.leyland.letool.rule.config;

import com.github.leyland.letool.rule.operation.*;
import com.github.leyland.letool.rule.service.RuleDictService;
import com.github.leyland.letool.rule.service.RuleValidationService;
import com.github.leyland.letool.rule.service.impl.RuleDictCacheServiceImpl;
import com.github.leyland.letool.rule.service.impl.RuleDictMemoryServiceImpl;
import com.github.leyland.letool.rule.service.impl.RuleValidationServiceImpl;
import com.github.leyland.letool.rule.validator.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

/**
 * 规则引擎自动配置
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Configuration
@EnableAsync
@ComponentScan(basePackages = "com.github.leyland.letool.rule")
@EnableConfigurationProperties(RuleEngineProperties.class)
@ConditionalOnProperty(prefix = "letool.rule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleEngineAutoConfiguration {

    public RuleEngineAutoConfiguration() {
        log.info("规则引擎自动配置初始化...");
    }

    // ==================== 核心运算执行器 ====================

    @Bean
    @ConditionalOnMissingBean
    public TypeConverter typeConverter() {
        log.info("初始化类型转换器");
        return new TypeConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public CoreOperationExecutor coreOperationExecutor(TypeConverter typeConverter) {
        log.info("初始化核心运算执行器");
        return new CoreOperationExecutor(typeConverter);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationExecutorFactory operationExecutorFactory(List<OperationExecutor> executors) {
        log.info("初始化运算执行器工厂");
        return new OperationExecutorFactory();
    }

    // ==================== 类型校验器 ====================

    @Bean
    @ConditionalOnMissingBean
    public StringFieldValidator stringFieldValidator() {
        log.info("初始化字符串字段验证器");
        return new StringFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public NumberFieldValidator numberFieldValidator() {
        log.info("初始化数字字段验证器");
        return new NumberFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public DateFieldValidator dateFieldValidator() {
        log.info("初始化日期字段验证器");
        return new DateFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public BooleanFieldValidator booleanFieldValidator() {
        log.info("初始化布尔字段验证器");
        return new BooleanFieldValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleExpressionValidator ruleExpressionValidator(List<FieldTypeValidator> validators) {
        log.info("初始化规则表达式验证器");
        return new RuleExpressionValidator(validators);
    }

    // ==================== 服务层 ====================

    @Bean
    @ConditionalOnMissingBean(RuleDictService.class)
    public RuleDictService ruleDictService() {
        log.info("初始化内存字典服务（默认实现）");
        return new RuleDictMemoryServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleDictCacheServiceImpl ruleDictCacheService(RuleDictService ruleDictService) {
        log.info("初始化字典缓存服务");
        return new RuleDictCacheServiceImpl(ruleDictService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuleValidationService ruleValidationService(
            RuleDictCacheServiceImpl cacheService,
            RuleDictService ruleDictService,
            RuleExpressionValidator expressionValidator,
            OperationExecutorFactory operationExecutorFactory,
            TypeConverter typeConverter) {
        log.info("初始化规则验证服务");
        return new RuleValidationServiceImpl(
                cacheService,
                ruleDictService,
                expressionValidator,
                operationExecutorFactory,
                typeConverter
        );
    }
}
