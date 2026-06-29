package com.github.leyland.letool.excel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Excel模块的Spring Boot自动配置类。
 *
 * <p>当 classpath 中存在 {@code com.alibaba.excel.EasyExcel} 时，
 * 此配置类将被自动加载。主要职责：
 * <ul>
 *   <li>确保 EasyExcel 库在运行时可用</li>
 *   <li>输出模块初始化日志，便于问题排查</li>
 * </ul>
 *
 * <p>注意：{@link com.github.leyland.letool.excel.util.ExcelUtil}
 * 全部为静态方法，无需注册为Bean，其功能在任何地方直接通过静态调用即可使用。
 *
 * <p>自动注册机制：通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 文件声明本类，Spring Boot 启动时自动扫描并加载。
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(com.alibaba.excel.EasyExcel.class)
public class ExcelAutoConfiguration {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(ExcelAutoConfiguration.class);

    // ======================== 构造器 ========================

    /**
     * 自动配置构造器。
     *
     * <p>在Bean实例化时输出初始化日志，表明Excel模块已就绪。
     * 如果此日志未出现在启动日志中，说明 EasyExcel 依赖缺失
     * 或自动配置导入未正确生效。
     */
    public ExcelAutoConfiguration() {
        log.info("letool-starter-excel initialized (EasyExcel)");
    }
}
