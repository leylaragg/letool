package io.github.leylaragg.letool.sms.model;

/**
 * 不可变短信模板参数。
 *
 * <p>参数同时保存名称和值，并由请求对象保持插入顺序。阿里云 Provider 使用参数名称构造
 * JSON 对象，腾讯云 Provider 按相同顺序提取参数值。</p>
 */
public final class SmsParameter {

    private final String name;
    private final String value;

    /**
     * 创建短信模板参数。
     *
     * @param name 参数名称
     * @param value 参数值
     */
    private SmsParameter(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("短信模板参数名称不能为空");
        }
        if (value == null) {
            throw new IllegalArgumentException("短信模板参数值不能为 null");
        }
        this.name = name;
        this.value = value;
    }

    /**
     * 创建短信模板参数。
     *
     * @param name 参数名称
     * @param value 参数值
     * @return 不可变模板参数
     */
    public static SmsParameter of(String name, String value) {
        return new SmsParameter(name, value);
    }

    /**
     * 获取参数名称。
     *
     * @return 参数名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取参数值。
     *
     * @return 参数值
     */
    public String getValue() {
        return value;
    }
}
