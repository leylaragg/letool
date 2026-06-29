package com.github.leyland.letool.ai.rag;

import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.core.ChatMessage;
import com.github.leyland.letool.ai.core.ChatResponse;
import com.github.leyland.letool.ai.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 服务 —— 检索增强生成（Retrieval-Augmented Generation）.
 *
 * <h3>RAG 流程</h3>
 * <ol>
 *   <li><b>索引阶段</b>：加载文档 -> 文本分块 -> 向量化 -> 存入向量数据库</li>
 *   <li><b>查询阶段</b>：用户问题 -> 向量化 -> 在向量数据库中检索最相似块 ->
 *       将相关块构建为上下文提示词 -> 调用 LLM 生成回答</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private RagService ragService;
 *
 * // 索引知识库
 * ragService.indexDocument("/data/knowledge.pdf");
 * ragService.index("这是一段需要被检索的知识文本");
 *
 * // 查询
 * String answer = ragService.query("什么是 Spring Boot？");
 *
 * // 带 TopK 的查询
 * String answer = ragService.query("什么是 Spring Boot？", 10);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    // ======================== 常量 ========================

    /** 默认检索数量 */
    public static final int DEFAULT_TOP_K = 5;

    /** 默认的分块大小 */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认的重叠大小 */
    public static final int DEFAULT_OVERLAP = 50;

    /** RAG 查询的默认系统提示词模板 */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个知识库助手。请根据以下提供的上下文信息回答用户的问题。" +
            "如果上下文中没有足够的信息，请诚实地回答你不知道，不要编造答案。" +
            "\n\n上下文信息：\n{{context}}";

    // ======================== 字段 ========================

    /** 嵌入服务 */
    private final EmbeddingService embeddingService;

    /** 向量存储 */
    private final VectorStore vectorStore;

    /** AI 模板引擎 */
    private final AiTemplate aiTemplate;

    /** 文本分块器 */
    private final TextSplitter textSplitter;

    /** 文档加载器 */
    private final DocumentLoader documentLoader;

    // ======================== 构造方法 ========================

    /**
     * 创建 RAG 服务实例.
     *
     * @param embeddingService 嵌入服务
     * @param vectorStore     向量存储
     * @param aiTemplate      AI 模板引擎
     */
    public RagService(EmbeddingService embeddingService, VectorStore vectorStore, AiTemplate aiTemplate) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.aiTemplate = aiTemplate;
        this.textSplitter = new TextSplitter(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
        this.documentLoader = new DocumentLoader();
    }

    // ======================== 索引方法 ========================

    /**
     * 索引纯文本内容.
     *
     * <p>流程：文本分块 -> 向量化 -> 存入向量数据库.</p>
     *
     * @param text 待索引的文本
     * @return 索引的文本块数量
     */
    public int index(String text) {
        return index(text, null);
    }

    /**
     * 索引纯文本内容（带元数据）.
     *
     * @param text     待索引的文本
     * @param metadata 元数据（如来源、作者等）
     * @return 索引的文本块数量
     */
    public int index(String text, Map<String, String> metadata) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("索引文本为空，跳过");
            return 0;
        }

        List<String> chunks = textSplitter.split(text);
        if (chunks.isEmpty()) {
            log.warn("文本分块结果为空");
            return 0;
        }

        // 批量向量化
        List<float[]> vectors = embeddingService.embed(chunks);

        // 存入向量数据库
        for (int i = 0; i < chunks.size(); i++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            vectorStore.add(id, vectors.get(i), chunks.get(i), metadata);
        }

        log.info("文本索引完成: chunks={}", chunks.size());
        return chunks.size();
    }

    /**
     * 索引文档文件.
     *
     * <p>流程：加载文件 -> 文本分块 -> 向量化 -> 存入向量数据库.</p>
     *
     * @param filePath 文件路径（支持 .txt、.md、.pdf 等格式）
     * @return 索引的文本块数量
     * @throws IOException 当文件读取失败时
     */
    public int indexDocument(String filePath) throws IOException {
        List<DocumentLoader.Document> documents = documentLoader.loadFromFile(filePath);
        int totalChunks = 0;
        for (DocumentLoader.Document doc : documents) {
            // 将来源信息放入元数据
            Map<String, String> metadata = doc.getMetadata();
            metadata.put("source", doc.getSource());
            totalChunks += index(doc.getContent(), metadata);
        }
        log.info("文档索引完成: file={}, total chunks={}", filePath, totalChunks);
        return totalChunks;
    }

    /**
     * 索引整个目录下的所有文档.
     *
     * @param directoryPath 目录路径
     * @return 索引的文本块数量
     * @throws IOException 当目录读取失败时
     */
    public int indexDirectory(String directoryPath) throws IOException {
        List<DocumentLoader.Document> documents = documentLoader.loadFromDirectory(directoryPath);
        int totalChunks = 0;
        for (DocumentLoader.Document doc : documents) {
            Map<String, String> metadata = doc.getMetadata();
            metadata.put("source", doc.getSource());
            totalChunks += index(doc.getContent(), metadata);
        }
        log.info("目录索引完成: dir={}, total chunks={}", directoryPath, totalChunks);
        return totalChunks;
    }

    // ======================== 查询方法 ========================

    /**
     * 使用 RAG 查询（默认 TopK=5）.
     *
     * <p>将用户问题向量化，在知识库中检索最相关的上下文，
     * 将上下文和问题一起发送给 LLM，生成增强后的回答.</p>
     *
     * @param question 用户问题
     * @return AI 生成的回答
     */
    public String query(String question) {
        return query(question, DEFAULT_TOP_K);
    }

    /**
     * 使用 RAG 查询（指定 TopK）.
     *
     * @param question 用户问题
     * @param topK     检索的上下文块数量
     * @return AI 生成的回答
     */
    public String query(String question, int topK) {
        if (vectorStore.isEmpty()) {
            log.warn("向量存储为空，请先索引文档");
            return "知识库为空，请先添加知识文档。";
        }

        // 步骤 1：检索相关上下文
        List<VectorStore.SearchResult> searchResults = vectorStore.searchByText(question, topK);
        if (searchResults.isEmpty()) {
            return "未找到相关信息。";
        }

        // 步骤 2：构建上下文字符串
        String context = buildContext(searchResults);

        // 步骤 3：构建增强后的提示词
        String systemPrompt = buildRagPrompt(context);

        // 步骤 4：调用 LLM
        ChatResponse response = aiTemplate.chat()
                .system(systemPrompt)
                .user(question)
                .execute();

        String answer = response.getContent();
        log.debug("RAG 查询完成: question={}, topK={}, answer length={}",
                question, topK, answer != null ? answer.length() : 0);
        return answer != null ? answer : "未能生成回答。";
    }

    /**
     * 仅检索相关上下文，不调用 LLM（适用于需要自行处理上下文的场景）.
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @return 搜索结果列表
     */
    public List<VectorStore.SearchResult> retrieve(String question, int topK) {
        return vectorStore.searchByText(question, topK);
    }

    /**
     * 仅检索相关上下文，不调用 LLM（默认 TopK=5）.
     *
     * @param question 用户问题
     * @return 搜索结果列表
     */
    public List<VectorStore.SearchResult> retrieve(String question) {
        return retrieve(question, DEFAULT_TOP_K);
    }

    // ======================== 统计与维护 ========================

    /**
     * 获取向量存储中的条目数量.
     *
     * @return 条目数
     */
    public int getEntryCount() {
        return vectorStore.size();
    }

    /**
     * 清空所有索引.
     */
    public void clearIndex() {
        vectorStore.clear();
        log.info("RAG 索引已清空");
    }

    // ======================== 内部方法 ========================

    /**
     * 将搜索结果拼接为上下文字符串.
     *
     * @param results 搜索结果列表
     * @return 格式化的上下文字符串
     */
    private String buildContext(List<VectorStore.SearchResult> results) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            VectorStore.SearchResult result = results.get(i);
            context.append("[片段 ").append(i + 1).append("]");
            if (result.getMetadata() != null && result.getMetadata().containsKey("source")) {
                context.append(" (来源: ").append(result.getMetadata().get("source")).append(")");
            }
            context.append("\n").append(result.getText()).append("\n\n");
        }
        return context.toString().trim();
    }

    /**
     * 构建 RAG 增强提示词.
     *
     * @param context 检索到的上下文
     * @return 系统提示词
     */
    private String buildRagPrompt(String context) {
        return DEFAULT_SYSTEM_PROMPT.replace("{{context}}", context);
    }
}
