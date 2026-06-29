package com.github.leyland.letool.ai.config;

import com.github.leyland.letool.ai.chat.ChatSession;
import com.github.leyland.letool.ai.chat.PromptTemplate;
import com.github.leyland.letool.ai.core.AiProvider;
import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.embedding.EmbeddingService;
import com.github.leyland.letool.ai.function.FunctionCallHandler;
import com.github.leyland.letool.ai.provider.DeepSeekProvider;
import com.github.leyland.letool.ai.provider.OpenAiProvider;
import com.github.leyland.letool.ai.provider.QwenProvider;
import com.github.leyland.letool.ai.rag.DocumentLoader;
import com.github.leyland.letool.ai.rag.RagService;
import com.github.leyland.letool.ai.rag.TextSplitter;
import com.github.leyland.letool.ai.rag.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * letool-starter-ai 自动配置 —— 注册 AI 模块的所有核心 Bean.
 *
 * <h3>注册的 Bean</h3>
 * <ul>
 *   <li>{@link AiTemplate} — AI 模板引擎（统一调用入口）</li>
 *   <li>{@link ChatSession} — 对话会话管理器（prototype 作用域）</li>
 *   <li>{@link EmbeddingService} — 嵌入服务（文本向量化 + 相似度计算）</li>
 *   <li>{@link VectorStore} — 内存向量存储</li>
 *   <li>{@link RagService} — RAG 检索增强生成服务（条件激活）</li>
 *   <li>{@link PromptTemplate} — 提示词模板引擎</li>
 *   <li>{@link FunctionCallHandler} — 函数调用处理器</li>
 *   <li>{@link DocumentLoader} — 文档加载器</li>
 *   <li>{@link TextSplitter} — 文本分块器</li>
 *   <li>各 AI 提供商：{@link OpenAiProvider}、{@link DeepSeekProvider}、{@link QwenProvider}</li>
 * </ul>
 *
 * <h3>激活条件</h3>
 * <p>当 {@code letool.ai.enabled=true}（默认）时激活.
 * 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 注册.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "letool.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiAutoConfiguration.class);

    // ======================== 提供商注册 ========================

    /**
     * 注册 OpenAI 提供商.
     *
     * @param properties AI 配置属性
     * @return OpenAI 提供商实例
     */
    @Bean
    public OpenAiProvider openAiProvider(AiProperties properties) {
        log.info("注册 OpenAI 提供商: model={}", properties.getOpenai().getDefaultModel());
        return new OpenAiProvider(properties.getOpenai());
    }

    /**
     * 注册 DeepSeek 提供商.
     *
     * @param properties AI 配置属性
     * @return DeepSeek 提供商实例
     */
    @Bean
    public DeepSeekProvider deepSeekProvider(AiProperties properties) {
        log.info("注册 DeepSeek 提供商: model={}", properties.getDeepseek().getDefaultModel());
        return new DeepSeekProvider(properties.getDeepseek());
    }

    /**
     * 注册通义千问（Qwen）提供商.
     *
     * @param properties AI 配置属性
     * @return 通义千问提供商实例
     */
    @Bean
    public QwenProvider qwenProvider(AiProperties properties) {
        log.info("注册通义千问提供商: model={}", properties.getQwen().getDefaultModel());
        return new QwenProvider(properties.getQwen());
    }

    // ======================== 核心 Bean ========================

    /**
     * 注册 AI 模板引擎 —— 统一的 LLM 调用入口.
     *
     * <p>自动收集所有注册的 {@link AiProvider} Bean 并管理.</p>
     *
     * @param providers  所有注册的 AI 提供商
     * @param properties AI 配置属性
     * @return AiTemplate 实例
     */
    @Bean
    public AiTemplate aiTemplate(List<AiProvider> providers, AiProperties properties) {
        return new AiTemplate(providers, properties);
    }

    /**
     * 注册对话会话管理器（prototype 作用域）.
     *
     * <p>每次注入获得一个新的会话实例，确保不同用户/对话互不干扰.</p>
     *
     * @param aiTemplate   AI 模板引擎
     * @param aiProperties AI 配置属性
     * @return 新的 ChatSession 实例
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatSession chatSession(AiTemplate aiTemplate, AiProperties aiProperties) {
        return new ChatSession(aiTemplate, aiProperties);
    }

    /**
     * 注册嵌入服务.
     *
     * @param aiTemplate AI 模板引擎
     * @return EmbeddingService 实例
     */
    @Bean
    public EmbeddingService embeddingService(AiTemplate aiTemplate) {
        return new EmbeddingService(aiTemplate);
    }

    // ======================== RAG 相关 Bean ========================

    /**
     * 注册内存向量存储.
     *
     * @param embeddingService 嵌入服务
     * @return VectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(EmbeddingService embeddingService) {
        return new VectorStore(embeddingService);
    }

    /**
     * 注册 RAG 检索增强生成服务（条件激活）.
     *
     * <p>仅当 {@link VectorStore} Bean 存在时激活.
     * 如果用户提供了自定义的 VectorStore 实现（如 Redis、Milvus），本服务自动适配.</p>
     *
     * @param embeddingService 嵌入服务
     * @param vectorStore      向量存储
     * @param aiTemplate       AI 模板引擎
     * @return RagService 实例
     */
    @Bean
    @ConditionalOnBean(VectorStore.class)
    public RagService ragService(EmbeddingService embeddingService,
                                 VectorStore vectorStore,
                                 AiTemplate aiTemplate) {
        log.info("RAG 服务已激活");
        return new RagService(embeddingService, vectorStore, aiTemplate);
    }

    // ======================== 工具类 Bean ========================

    /**
     * 注册提示词模板引擎.
     *
     * @return PromptTemplate 实例
     */
    @Bean
    public PromptTemplate promptTemplate() {
        return new PromptTemplate();
    }

    /**
     * 注册函数调用处理器.
     *
     * @return FunctionCallHandler 实例
     */
    @Bean
    public FunctionCallHandler functionCallHandler() {
        return new FunctionCallHandler();
    }

    /**
     * 注册文档加载器.
     *
     * @return DocumentLoader 实例
     */
    @Bean
    public DocumentLoader documentLoader() {
        return new DocumentLoader();
    }

    /**
     * 注册文本分块器（使用默认参数）.
     *
     * @return TextSplitter 实例
     */
    @Bean
    public TextSplitter textSplitter() {
        return new TextSplitter();
    }
}
