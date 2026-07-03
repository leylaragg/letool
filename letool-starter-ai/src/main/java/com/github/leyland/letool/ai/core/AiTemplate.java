package com.github.leyland.letool.ai.core;

import com.github.leyland.letool.ai.config.AiProperties;
import com.github.leyland.letool.ai.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 模板引擎 —— 统一的 LLM 调用入口，提供流畅的构建器 API.
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>管理所有注册的 {@link AiProvider} 实例</li>
 *   <li>提供 {@link #chat()} 和 {@link #embedding()} 流畅构建器</li>
 *   <li>根据提供商名称路由请求到正确的 Provider</li>
 *   <li>自动填充默认值（模型、温度、Token 数等）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private AiTemplate aiTemplate;
 *
 * // 简单对话
 * ChatResponse response = aiTemplate.chat()
 *     .provider("openai")
 *     .model("gpt-4o")
 *     .system("你是一个助手")
 *     .user("你好")
 *     .execute();
 *
 * System.out.println(response.getContent());
 *
 * // 流式对话
 * aiTemplate.chat()
 *     .provider("openai")
 *     .user("写一段欢迎语")
 *     .executeStream(System.out::print);
 *
 * // 获取嵌入向量
 * float[] vector = aiTemplate.embedding()
 *     .provider("openai")
 *     .model("text-embedding-3-small")
 *     .input("Hello World")
 *     .executeSingle();
 *
 * // 查询可用提供商
 * List<String> providers = aiTemplate.listProviders();
 *
 * // 查询可用模型
 * List<String> models = aiTemplate.listModels("deepseek");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AiTemplate {

    private static final Logger log = LoggerFactory.getLogger(AiTemplate.class);

    // ======================== 字段 ========================

    /**
     * 提供商映射（providerName -> AiProvider）
     */
    private final Map<String, AiProvider> providers;

    /**
     * AI 全局配置
     */
    private final AiProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 创建 AiTemplate 实例.
     *
     * <p>将提供商列表转换为 Map 以便按名称查找.</p>
     *
     * @param providers  注册的提供商列表
     * @param properties AI 全局配置
     */
    public AiTemplate(List<AiProvider> providers, AiProperties properties) {
        this.properties = properties;
        this.providers = new LinkedHashMap<>();
        if (providers != null) {
            for (AiProvider provider : providers) {
                this.providers.put(provider.getProviderName().toLowerCase(), provider);
            }
        }
        log.info("AiTemplate 初始化完成，已注册提供商: {}", this.providers.keySet());
    }

    // ======================== 构建器入口 ========================

    /**
     * 创建对话构建器 —— 用于构建和发送对话请求.
     *
     * @return {@link ChatBuilder} 实例
     */
    public ChatBuilder chat() {
        return new ChatBuilder();
    }

    /**
     * 创建嵌入构建器 —— 用于构建和发送嵌入请求.
     *
     * @return {@link EmbeddingBuilder} 实例
     */
    public EmbeddingBuilder embedding() {
        return new EmbeddingBuilder();
    }

    // ======================== 提供商查询 ========================

    /**
     * 列出所有已注册的提供商名称.
     *
     * @return 提供商名称列表
     */
    public List<String> listProviders() {
        return new ArrayList<>(providers.keySet());
    }

    /**
     * 列出指定提供商的可用模型.
     *
     * @param provider 提供商名称
     * @return 模型名称列表
     * @throws AiException 如果提供商不存在
     */
    public List<String> listModels(String provider) {
        AiProvider p = getProvider(provider);
        return p.getAvailableModels();
    }

    // ======================== 内部工具方法 ========================

    /**
     * 根据名称获取提供商.
     *
     * @param name 提供商名称，若为空则使用默认提供商
     * @return {@link AiProvider} 实例
     * @throws AiException 如果提供商不存在
     */
    protected AiProvider getProvider(String name) {
        String resolvedName = (name != null && !name.isEmpty())
                ? name.toLowerCase()
                : properties.getDefaultProvider().toLowerCase();
        AiProvider provider = providers.get(resolvedName);
        if (provider == null) {
            throw new AiException(
                    "未找到 AI 提供商: " + resolvedName + "，可用提供商: " + providers.keySet(),
                    resolvedName);
        }
        if (!provider.isAvailable()) {
            throw new AiException(
                    "AI 提供商不可用: " + resolvedName + "，请检查 API 密钥配置",
                    resolvedName);
        }
        return provider;
    }

    // ======================== 内部类：ChatBuilder ========================

    /**
     * 对话构建器 —— 流畅的链式 API，最后调用 {@link #execute()} 发送请求.
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * ChatResponse resp = aiTemplate.chat()
     *     .provider("openai")
     *     .model("gpt-4o")
     *     .system("你是助手")
     *     .user("你好")
     *     .temperature(0.8)
     *     .maxTokens(2048)
     *     .functions(functionDefinitions)
     *     .execute();
     * }</pre>
     */
    public class ChatBuilder {
        private String provider;
        private String model;
        private List<ChatMessage> messages = new ArrayList<>();
        private double temperature = -1;
        private int maxTokens = -1;
        private List<FunctionDefinition> functions;

        /**
         * 设置提供商名称.
         *
         * @param provider 提供商名称
         * @return this
         */
        public ChatBuilder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置模型名称.
         *
         * @param model 模型名称
         * @return this
         */
        public ChatBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 设置系统提示词.
         *
         * @param content 系统提示词
         * @return this
         */
        public ChatBuilder system(String content) {
            this.messages.add(ChatMessage.system(content));
            return this;
        }

        /**
         * 设置用户消息.
         *
         * @param content 用户输入
         * @return this
         */
        public ChatBuilder user(String content) {
            this.messages.add(ChatMessage.user(content));
            return this;
        }

        /**
         * 添加助手消息.
         *
         * @param content 助手消息内容
         * @return this
         */
        public ChatBuilder assistant(String content) {
            this.messages.add(ChatMessage.assistant(content));
            return this;
        }

        /**
         * 添加函数返回消息.
         *
         * @param name    函数名称
         * @param content 函数返回内容
         * @return this
         */
        public ChatBuilder function(String name, String content) {
            this.messages.add(ChatMessage.function(name, content));
            return this;
        }

        /**
         * 添加消息列表.
         *
         * @param messages 消息列表
         * @return this
         */
        public ChatBuilder messages(List<ChatMessage> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        /**
         * 添加单条消息.
         *
         * @param message 消息对象
         * @return this
         */
        public ChatBuilder message(ChatMessage message) {
            this.messages.add(message);
            return this;
        }

        /**
         * 设置温度参数.
         *
         * @param temperature 温度值（0.0~2.0）
         * @return this
         */
        public ChatBuilder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置最大生成 Token 数.
         *
         * @param maxTokens Token 上限
         * @return this
         */
        public ChatBuilder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 设置函数定义列表（Function Calling）.
         *
         * @param functions 函数定义列表
         * @return this
         */
        public ChatBuilder functions(List<FunctionDefinition> functions) {
            this.functions = functions;
            return this;
        }

        /**
         * 构建请求并执行，返回 AI 响应.
         *
         * <p>自动填充默认值：如果未设置 provider，使用默认提供商；
         * 如果未设置 temperature/maxTokens，使用全局配置的默认值.</p>
         *
         * @return 对话响应
         * @throws AiException 当调用失败时
         */
        public ChatResponse execute() {
            ChatInvocation invocation = getProviderAndRequest();
            return invocation.provider.chat(invocation.request);
        }

        /**
         * 构建请求并执行流式对话，逐段回调增量文本。
         *
         * @param onDelta 增量文本回调
         * @throws AiException 当调用失败或 provider 不支持流式输出时
         */
        public void executeStream(Consumer<String> onDelta) {
            ChatInvocation invocation = getProviderAndRequest();
            invocation.provider.chatStream(invocation.request, onDelta);
        }

        /**
         * 解析 provider 并构建最终请求。
         *
         * @return 对话调用上下文
         */
        private ChatInvocation getProviderAndRequest() {
            String resolvedProvider = (provider != null && !provider.isEmpty())
                    ? provider : properties.getDefaultProvider();
            AiProvider aiProvider = getProvider(resolvedProvider);

            ChatRequest request = ChatRequest.builder()
                    .provider(resolvedProvider)
                    .model(model != null ? model : properties.getProviderConfig(resolvedProvider).getDefaultModel())
                    .messages(messages)
                    .temperature(temperature >= 0 ? temperature : properties.getChat().getDefaultTemperature())
                    .maxTokens(maxTokens > 0 ? maxTokens : properties.getChat().getDefaultMaxTokens())
                    .functions(functions)
                    .build();

            return new ChatInvocation(aiProvider, request);
        }

        /**
         * 对话调用上下文。
         *
         * @param provider AI 提供商
         * @param request  对话请求
         */
        private record ChatInvocation(AiProvider provider, ChatRequest request) {
        }
    }

    // ======================== 内部类：EmbeddingBuilder ========================

    /**
     * 嵌入构建器 —— 流畅的链式 API，最后调用 {@link #execute()} 发送请求.
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * EmbeddingResponse resp = aiTemplate.embedding()
     *     .provider("openai")
     *     .model("text-embedding-3-small")
     *     .input("Hello World")
     *     .input("你好世界")
     *     .execute();
     *
     * float[] vec = aiTemplate.embedding()
     *     .input("Hello")
     *     .executeSingle();
     * }</pre>
     */
    public class EmbeddingBuilder {
        private String provider;
        private String model;
        private List<String> input = new ArrayList<>();

        /**
         * 设置提供商名称.
         *
         * @param provider 提供商名称
         * @return this
         */
        public EmbeddingBuilder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置嵌入模型名称.
         *
         * @param model 模型名称
         * @return this
         */
        public EmbeddingBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 添加待向量化的文本.
         *
         * @param text 文本内容
         * @return this
         */
        public EmbeddingBuilder input(String text) {
            this.input.add(text);
            return this;
        }

        /**
         * 批量添加文本.
         *
         * @param texts 文本列表
         * @return this
         */
        public EmbeddingBuilder input(List<String> texts) {
            if (texts != null) {
                this.input.addAll(texts);
            }
            return this;
        }

        /**
         * 构建请求并执行，返回嵌入响应.
         *
         * @return 嵌入响应
         */
        public EmbeddingResponse execute() {
            String resolvedProvider = (provider != null && !provider.isEmpty())
                    ? provider : properties.getDefaultProvider();
            AiProvider aiProvider = getProvider(resolvedProvider);

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .provider(resolvedProvider)
                    .model(model != null ? model : properties.getEmbedding().getDefaultModel())
                    .input(input)
                    .build();

            return aiProvider.embedding(request);
        }

        /**
         * 执行嵌入请求并返回第一条文本的向量.
         *
         * <p>适用于只需要嵌入单条文本的场景.</p>
         *
         * @return 浮点向量数组
         * @throws AiException 如果响应中无数据
         */
        public float[] executeSingle() {
            EmbeddingResponse response = execute();
            if (response.getData() == null || response.getData().isEmpty()) {
                throw new AiException("嵌入响应中没有数据", provider);
            }
            return response.getData().get(0).getEmbedding();
        }
    }
}
