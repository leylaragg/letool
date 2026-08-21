package io.github.leylaragg.letool.print.autoconfigure;

import io.github.leylaragg.letool.print.observability.PrintInfrastructureHealthIndicator;
import io.github.leylaragg.letool.print.observability.PrintTemplateHealthIndicator;
import io.github.leylaragg.letool.print.pdf.PdfFont;
import io.github.leylaragg.letool.print.service.PrintService;
import io.github.leylaragg.letool.print.template.TemplateSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Actuator 存在时装配模板和 PDF 基础设施健康检查。
 *
 * @author leyland
 */
@AutoConfiguration(after = PrintAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(PrintService.class)
@ConditionalOnProperty(
        prefix = "letool.print.health",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PrintHealthAutoConfiguration {

    /**
     * @param source 模板只读来源
     * @param properties 打印配置
     * @return 不读取模板正文的仓库健康检查
     */
    @Bean
    @ConditionalOnBean({TemplateSource.class, PrintProperties.class})
    @ConditionalOnMissingBean(PrintTemplateHealthIndicator.class)
    public PrintTemplateHealthIndicator printTemplateHealthIndicator(
            TemplateSource source, PrintProperties properties) {
        return new PrintTemplateHealthIndicator(source, properties);
    }

    /**
     * @param fonts 宿主提供的 PDF 字体
     * @param properties 打印配置
     * @return 不暴露字体内容和目录路径的基础设施健康检查
     */
    @Bean
    @ConditionalOnBean(PrintProperties.class)
    @ConditionalOnMissingBean(PrintInfrastructureHealthIndicator.class)
    public PrintInfrastructureHealthIndicator printInfrastructureHealthIndicator(
            ObjectProvider<PdfFont> fonts, PrintProperties properties) {
        return new PrintInfrastructureHealthIndicator(fonts.orderedStream().toList(), properties);
    }
}
