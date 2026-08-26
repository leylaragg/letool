package io.github.leylaragg.letool.redis.serializer.allowed;

/**
 * 位于 Redis 自动类型显式允许包中的测试对象。
 */
public class AllowedValue {

    private String value;

    /**
     * 创建供 JSON 反序列化使用的空对象。
     */
    public AllowedValue() {
    }

    /**
     * 使用测试内容创建对象。
     *
     * @param value 测试内容
     */
    public AllowedValue(String value) {
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
