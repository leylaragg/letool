package com.github.leyland.letool.ai.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 嵌入请求模型 —— 封装文本向量化请求的参数.
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * EmbeddingRequest request = EmbeddingRequest.builder()
 *     .provider("openai")
 *     .model("text-embedding-3-small")
 *     .input("你好世界")
 *     .input("Hello World")
 *     .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class EmbeddingRequest {

    // ======================== 字段 ========================

    /** 提供商名称 */
    private String provider;

    /** 嵌入模型名称 */
    private String model;

    /** 待向量化的文本列表 */
    private List<String> input;

    // ======================== 构造方法 ========================

    /** 默认构造，初始化文本列表 */
    public EmbeddingRequest() {
        this.input = new ArrayList<>();
    }

    // ======================== 静态工厂：Builder ========================

    /**
     * 创建构建器.
     *
     * @return {@link Builder} 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ======================== Builder 内部类 ========================

    /**
     * EmbeddingRequest 构建器 —— 支持链式调用.
     */
    public static class Builder {
        private String provider;
        private String model;
        private List<String> input = new ArrayList<>();

        /**
         * 设置提供商名称.
         *
         * @param provider 提供商名称
         * @return this
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置嵌入模型名称.
         *
         * @param model 模型名称
         * @return this
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 添加一条待向量化的文本.
         *
         * @param text 文本内容
         * @return this
         */
        public Builder input(String text) {
            this.input.add(text);
            return this;
        }

        /**
         * 设置文本列表（覆盖已有的）.
         *
         * @param texts 文本列表
         * @return this
         */
        public Builder input(List<String> texts) {
            this.input = new ArrayList<>(texts);
            return this;
        }

        /**
         * 添加多条文本.
         *
         * @param texts 文本数组
         * @return this
         */
        public Builder input(String... texts) {
            this.input.addAll(Arrays.asList(texts));
            return this;
        }

        /**
         * 构建 EmbeddingRequest 实例.
         *
         * @return 构建好的请求对象
         */
        public EmbeddingRequest build() {
            EmbeddingRequest request = new EmbeddingRequest();
            request.provider = this.provider;
            request.model = this.model;
            request.input = this.input;
            return request;
        }
    }

    // ======================== getter / setter ========================

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<String> getInput() { return input; }
    public void setInput(List<String> input) { this.input = input; }
}
