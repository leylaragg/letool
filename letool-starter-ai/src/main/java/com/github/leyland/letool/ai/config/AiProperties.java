package com.github.leyland.letool.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * letool AI 模块统一配置属性 —— 管理所有 AI 提供商的 API 密钥、端点、默认模型及对话参数.
 *
 * <h3>配置前缀</h3>
 * <pre>{@code letool.ai}</pre>
 *
 * <h3>YAML 示例</h3>
 * <pre>{@code
 * letool:
 *   ai:
 *     enabled: true
 *     default-provider: openai
 *     openai:
 *       api-key: sk-xxx
 *       base-url: https://api.openai.com/v1
 *       default-model: gpt-4o
 *       connect-timeout-millis: 10000
 *       read-timeout-millis: 60000
 *       max-retries: 2
 *       retry-backoff-millis: 200
 *     azure:
 *       api-key: xxx
 *       endpoint: https://my-resource.openai.azure.com
 *       deployment-id: gpt-4-deployment
 *     deepseek:
 *       api-key: sk-xxx
 *     qwen:
 *       api-key: sk-xxx
 *     zhipu:
 *       api-key: xxx
 *     ollama:
 *       base-url: http://localhost:11434
 *       default-model: llama3
 *     chat:
 *       max-history-length: 20
 *       default-temperature: 0.7
 *       default-max-tokens: 4096
 *     embedding:
 *       default-model: text-embedding-3-small
 *     rate-limit:
 *       tokens-per-minute: 100000
 *       requests-per-minute: 100
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
@ConfigurationProperties(prefix = "letool.ai")
public class AiProperties {

    // ======================== 全局设置 ========================

    /**
     * 是否启用 AI 模块，默认 {@code true}
     */
    private boolean enabled = true;

    /**
     * 默认 AI 提供商名称，如 "openai"、"deepseek" 等
     */
    private String defaultProvider = "openai";

    // ======================== 各提供商配置 ========================

    /**
     * OpenAI 提供商配置
     */
    private OpenAi openai = new OpenAi();

    /**
     * Azure OpenAI 提供商配置
     */
    private Azure azure = new Azure();

    /**
     * DeepSeek 提供商配置
     */
    private DeepSeek deepseek = new DeepSeek();

    /**
     * 通义千问（Qwen）提供商配置
     */
    private Qwen qwen = new Qwen();

    /**
     * 智谱（Zhipu）提供商配置
     */
    private Zhipu zhipu = new Zhipu();

    /**
     * Ollama 本地模型提供商配置
     */
    private Ollama ollama = new Ollama();

    /**
     * 对话参数配置
     */
    private Chat chat = new Chat();

    /**
     * 嵌入模型配置
     */
    private Embedding embedding = new Embedding();

    /**
     * 速率限制配置
     */
    private RateLimit rateLimit = new RateLimit();

    // ======================== 自定义提供商（扩展） ========================

    /**
     * 自定义提供商配置映射，key 为提供商名称，value 为提供商配置
     */
    private Map<String, Provider> customProviders = new HashMap<>();

    // ======================== getter / setter ========================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAi openai) {
        this.openai = openai;
    }

    public Azure getAzure() {
        return azure;
    }

    public void setAzure(Azure azure) {
        this.azure = azure;
    }

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(DeepSeek deepseek) {
        this.deepseek = deepseek;
    }

    public Qwen getQwen() {
        return qwen;
    }

    public void setQwen(Qwen qwen) {
        this.qwen = qwen;
    }

    public Zhipu getZhipu() {
        return zhipu;
    }

    public void setZhipu(Zhipu zhipu) {
        this.zhipu = zhipu;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Map<String, Provider> getCustomProviders() {
        return customProviders;
    }

    public void setCustomProviders(Map<String, Provider> customProviders) {
        this.customProviders = customProviders;
    }

    /**
     * 根据提供商名称获取对应的配置.
     *
     * @param providerName 提供商名称（如 "openai"、"deepseek" 等）
     * @return 对应的 {@link Provider} 配置，未找到返回 {@code null}
     */
    public Provider getProviderConfig(String providerName) {
        if (providerName == null) return null;
        switch (providerName.toLowerCase()) {
            case "openai":
                return openai;
            case "azure":
                return azure;
            case "deepseek":
                return deepseek;
            case "qwen":
                return qwen;
            case "zhipu":
                return zhipu;
            case "ollama":
                return ollama;
            default:
                return customProviders.get(providerName);
        }
    }

    // ======================== 内部类：提供商基类 ========================

    /**
     * AI 提供商基础配置 —— 所有提供商的公共属性.
     *
     * <p>具体的提供商（如 OpenAI、DeepSeek）继承此类并设置各自的默认值.</p>
     */
    public static class Provider {

        /**
         * API 密钥
         */
        private String apiKey;

        /**
         * API 基础 URL
         */
        private String baseUrl;

        /**
         * 默认模型名称
         */
        private String defaultModel;

        /**
         * 连接超时时间（毫秒），默认 10 秒。
         */
        private int connectTimeoutMillis = 10000;

        /**
         * 读取超时时间（毫秒），默认 60 秒。
         */
        private int readTimeoutMillis = 60000;

        /**
         * 临时错误最大重试次数，默认重试 2 次。
         */
        private int maxRetries = 2;

        /**
         * 重试退避时间（毫秒），默认 200 毫秒。
         */
        private long retryBackoffMillis = 200;

        // ======================== getter / setter ========================

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMillis() {
            return retryBackoffMillis;
        }

        public void setRetryBackoffMillis(long retryBackoffMillis) {
            this.retryBackoffMillis = retryBackoffMillis;
        }
    }

    // ======================== 内部类：OpenAI 配置 ========================

    /**
     * OpenAI 提供商配置.
     *
     * <p>默认模型为 <b>gpt-4o</b>，默认终端为 {@code https://api.openai.com/v1}.</p>
     */
    public static class OpenAi extends Provider {
        public OpenAi() {
            setDefaultModel("gpt-4o");
            setBaseUrl("https://api.openai.com/v1");
        }
    }

    // ======================== 内部类：Azure OpenAI 配置 ========================

    /**
     * Azure OpenAI 提供商配置.
     *
     * <p>Azure OpenAI 使用独立的 {@code endpoint} 和 {@code deploymentId} 参数，
     * 同时继承 {@link Provider} 基类的通用字段.
     * 如果不设置 {@code baseUrl}，会自动从 {@code endpoint} 推导.</p>
     */
    public static class Azure extends Provider {

        /**
         * Azure OpenAI 资源端点，如 {@code https://my-resource.openai.azure.com}
         */
        private String endpoint;

        /**
         * 部署 ID（即模型部署名称，同时映射到基类的 defaultModel）
         */
        private String deploymentId;

        // ======================== getter / setter ========================

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getDeploymentId() {
            return deploymentId;
        }

        public void setDeploymentId(String deploymentId) {
            this.deploymentId = deploymentId;
        }

        /**
         * Azure 的 baseUrl 从 endpoint 推导，若单独设置则优先使用
         */
        @Override
        public String getBaseUrl() {
            if (super.getBaseUrl() != null) return super.getBaseUrl();
            return endpoint;
        }

        /**
         * Azure 的 defaultModel 映射到 deploymentId
         */
        @Override
        public String getDefaultModel() {
            if (super.getDefaultModel() != null) return super.getDefaultModel();
            return deploymentId;
        }
    }

    // ======================== 内部类：DeepSeek 配置 ========================

    /**
     * DeepSeek 提供商配置.
     *
     * <p>默认模型为 <b>deepseek-chat</b>，默认终端为 {@code https://api.deepseek.com/v1}.
     * API 格式与 OpenAI 兼容.</p>
     */
    public static class DeepSeek extends Provider {
        public DeepSeek() {
            setDefaultModel("deepseek-chat");
            setBaseUrl("https://api.deepseek.com/v1");
        }
    }

    // ======================== 内部类：通义千问 配置 ========================

    /**
     * 通义千问（Qwen）提供商配置.
     *
     * <p>默认模型为 <b>qwen-max</b>，默认终端为阿里云 DashScope 兼容模式端点.
     * API 格式与 OpenAI 兼容.</p>
     */
    public static class Qwen extends Provider {
        public Qwen() {
            setDefaultModel("qwen-max");
            setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        }
    }

    // ======================== 内部类：智谱 配置 ========================

    /**
     * 智谱（Zhipu）提供商配置.
     *
     * <p>默认模型为 <b>glm-4</b>，默认终端为智谱开放平台 v4 API.</p>
     */
    public static class Zhipu extends Provider {
        public Zhipu() {
            setDefaultModel("glm-4");
            setBaseUrl("https://open.bigmodel.cn/api/paas/v4");
        }
    }

    // ======================== 内部类：Ollama 配置 ========================

    /**
     * Ollama 本地模型提供商配置.
     *
     * <p>默认模型为 <b>llama3</b>，默认终端为本地 {@code http://localhost:11434}.
     * Ollama 通常无需 API 密钥.</p>
     */
    public static class Ollama extends Provider {
        public Ollama() {
            setDefaultModel("llama3");
            setBaseUrl("http://localhost:11434");
        }
    }

    // ======================== 内部类：对话参数 ========================

    /**
     * 对话参数配置 —— 控制会话行为、温度、最大 Token 数.
     */
    public static class Chat {

        /**
         * 最大历史记录长度，超过后自动裁剪最早的消息，默认 {@code 20}
         */
        private int maxHistoryLength = 20;

        /**
         * 默认温度参数（0.0~2.0），值越高输出越随机，默认 {@code 0.7}
         */
        private double defaultTemperature = 0.7;

        /**
         * 默认最大生成 Token 数，默认 {@code 4096}
         */
        private int defaultMaxTokens = 4096;

        // ======================== getter / setter ========================

        public int getMaxHistoryLength() {
            return maxHistoryLength;
        }

        public void setMaxHistoryLength(int maxHistoryLength) {
            this.maxHistoryLength = maxHistoryLength;
        }

        public double getDefaultTemperature() {
            return defaultTemperature;
        }

        public void setDefaultTemperature(double defaultTemperature) {
            this.defaultTemperature = defaultTemperature;
        }

        public int getDefaultMaxTokens() {
            return defaultMaxTokens;
        }

        public void setDefaultMaxTokens(int defaultMaxTokens) {
            this.defaultMaxTokens = defaultMaxTokens;
        }
    }

    // ======================== 内部类：嵌入模型 ========================

    /**
     * 嵌入模型配置.
     *
     * <p>默认使用 OpenAI 的 <b>text-embedding-3-small</b> 模型.</p>
     */
    public static class Embedding {

        /**
         * 默认嵌入模型名称
         */
        private String defaultModel = "text-embedding-3-small";

        // ======================== getter / setter ========================

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }
    }

    // ======================== 内部类：速率限制 ========================

    /**
     * 速率限制配置 —— 控制每分钟的 Token 和请求数量上限.
     */
    public static class RateLimit {

        /**
         * 每分钟最大 Token 数，默认 {@code 100000}
         */
        private int tokensPerMinute = 100000;

        /**
         * 每分钟最大请求数，默认 {@code 100}
         */
        private int requestsPerMinute = 100;

        // ======================== getter / setter ========================

        public int getTokensPerMinute() {
            return tokensPerMinute;
        }

        public void setTokensPerMinute(int tokensPerMinute) {
            this.tokensPerMinute = tokensPerMinute;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }
}
