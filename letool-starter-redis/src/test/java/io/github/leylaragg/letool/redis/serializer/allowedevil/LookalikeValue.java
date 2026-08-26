package io.github.leylaragg.letool.redis.serializer.allowedevil;

/**
 * 所在包仅与允许包共享原始字符串前缀的越界测试对象。
 */
public class LookalikeValue {

    private String value;

    /**
     * 创建供 JSON 反序列化使用的空对象。
     */
    public LookalikeValue() {
    }

    /**
     * 使用测试内容创建对象。
     *
     * @param value 测试内容
     */
    public LookalikeValue(String value) {
        this.value = value;
    }

    /**
     * 返回测试内容。
     *
     * @return 测试内容
     */
    public String getValue() {
        return value;
    }

    /**
     * 更新测试内容。
     *
     * @param value 测试内容
     */
    public void setValue(String value) {
        this.value = value;
    }
}
