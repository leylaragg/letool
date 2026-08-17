package io.github.leylaragg.letool.print.service;

import io.github.leylaragg.letool.print.api.RenderOptions;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * 业务打印门面在启动阶段冻结的运行时配置。
 *
 * @author leyland
 */
public final class PrintRuntimeSettings {

    /** 参与 XML 编译缓存键的渲染器配置版本。 */
    private final long rendererProfileVersion;

    /** 请求默认使用的文本区域。 */
    private final Locale locale;

    /** 请求默认使用的日期时间时区。 */
    private final ZoneId zoneId;

    /** 请求统一使用的渲染限制。 */
    private final RenderOptions renderOptions;

    /**
     * 创建不可变运行时配置快照。
     *
     * @param rendererProfileVersion 正整数渲染器配置版本
     * @param locale 默认区域
     * @param zoneId 默认时区
     * @param renderOptions 默认渲染限制
     * @throws IllegalArgumentException 渲染器配置版本不是正整数时抛出
     * @throws NullPointerException 区域、时区或渲染限制为空时抛出
     */
    public PrintRuntimeSettings(
            long rendererProfileVersion,
            Locale locale,
            ZoneId zoneId,
            RenderOptions renderOptions) {
        if (rendererProfileVersion <= 0) {
            throw new IllegalArgumentException("rendererProfileVersion 必须为正整数");
        }
        this.rendererProfileVersion = rendererProfileVersion;
        this.locale = Objects.requireNonNull(locale, "locale 不能为空");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId 不能为空");
        this.renderOptions = Objects.requireNonNull(renderOptions, "renderOptions 不能为空");
    }

    /** @return 渲染器配置版本 */
    public long rendererProfileVersion() {
        return rendererProfileVersion;
    }

    /** @return 默认文本区域 */
    public Locale locale() {
        return locale;
    }

    /** @return 默认日期时间时区 */
    public ZoneId zoneId() {
        return zoneId;
    }

    /** @return 默认渲染限制 */
    public RenderOptions renderOptions() {
        return renderOptions;
    }
}
