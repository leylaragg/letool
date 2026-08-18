package io.github.leylaragg.letool.print.observability;

import io.github.leylaragg.letool.print.service.PrintExecutionSnapshot;
import io.github.leylaragg.letool.print.service.PrintTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 把安全打印快照写入 Micrometer。
 *
 * <p>标签只使用输出格式、执行结果和框架固定失败分类。</p>
 *
 * @author leyland
 */
public final class MicrometerPrintTelemetry implements PrintTelemetry {

    /** 宿主提供的指标注册表。 */
    private final MeterRegistry registry;

    /**
     * @param registry 指标注册表
     */
    public MicrometerPrintTelemetry(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
    }

    /**
     * 记录耗时，并按执行结果补充失败或产物指标。
     *
     * @param snapshot 不含业务数据的执行快照
     */
    @Override
    public void record(PrintExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot 不能为空");
        String result = snapshot.success() ? "success" : "failure";
        String failure = snapshot.failure().value();
        Timer.builder("letool.print.render.duration")
                .tag("output", snapshot.outputFormat())
                .tag("result", result)
                .tag("failure", failure)
                .register(registry)
                .record(snapshot.durationNanos(), TimeUnit.NANOSECONDS);

        if (snapshot.success()) {
            recordOutput(snapshot);
        } else {
            Counter.builder("letool.print.render.failures")
                    .tag("output", snapshot.outputFormat())
                    .tag("failure", failure)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 成功产物只按输出格式聚合页数和字节数。
     *
     * @param snapshot 成功执行快照
     */
    private void recordOutput(PrintExecutionSnapshot snapshot) {
        DistributionSummary.builder("letool.print.output.bytes")
                .baseUnit("bytes")
                .tag("output", snapshot.outputFormat())
                .register(registry)
                .record(snapshot.outputBytes());
        DistributionSummary.builder("letool.print.output.pages")
                .baseUnit("pages")
                .tag("output", snapshot.outputFormat())
                .register(registry)
                .record(snapshot.pageCount());
    }
}
