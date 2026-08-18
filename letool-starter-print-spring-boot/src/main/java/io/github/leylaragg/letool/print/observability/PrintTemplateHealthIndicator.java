package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.autoconfigure.PrintProperties;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSet;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;
import java.util.Optional;

/**
 * 检查模板仓库调用和当前激活集合。
 *
 * @author leyland
 */
public final class PrintTemplateHealthIndicator implements HealthIndicator {

    /** 待读取的模板仓库。 */
    private final TemplateRepository repository;

    /** 决定空仓库是否属于故障的启动配置。 */
    private final PrintProperties properties;

    /**
     * @param repository 模板仓库
     * @param properties 打印配置
     */
    public PrintTemplateHealthIndicator(TemplateRepository repository, PrintProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /** 只输出活动状态、版本和摘要。 */
    @Override
    public Health health() {
        Optional<TemplateSet> current;
        try {
            current = repository.current();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("repository", "unavailable").build();
        }
        if (current.isEmpty()) {
            Health.Builder builder = properties.getStartup().isRequireActiveTemplate()
                    ? Health.down() : Health.up();
            return builder.withDetail("active", false).build();
        }
        TemplateSet templateSet = current.orElseThrow();
        return Health.up()
                .withDetail("active", true)
                .withDetail("version", templateSet.version())
                .withDetail("digest", templateSet.digest())
                .build();
    }
}
