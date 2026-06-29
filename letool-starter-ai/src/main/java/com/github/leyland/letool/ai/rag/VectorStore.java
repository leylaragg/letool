package com.github.leyland.letool.ai.rag;

import com.github.leyland.letool.ai.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 内存向量存储 —— 基于余弦相似度的简单向量数据库.
 *
 * <h3>特点</h3>
 * <ul>
 *   <li>纯内存存储，无需外部数据库</li>
 *   <li>线程安全的 CopyOnWriteArrayList 实现</li>
 *   <li>基于余弦相似度进行向量搜索</li>
 *   <li>适用于中小规模数据集（万级以内）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * VectorStore store = new VectorStore(embeddingService);
 *
 * // 添加向量
 * store.add("doc1", vector, "这是文档内容", Map.of("author", "张三"));
 *
 * // 搜索最相似的 5 条
 * List<SearchResult> results = store.search(queryVector, 5);
 *
 * // 删除
 * store.delete("doc1");
 *
 * // 清空
 * store.clear();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    // ======================== 字段 ========================

    /** 向量条目列表（线程安全） */
    private final List<VectorEntry> entries;

    /** 嵌入服务（用于搜索时计算相似度） */
    private final EmbeddingService embeddingService;

    // ======================== 构造方法 ========================

    /**
     * 创建向量存储实例.
     *
     * @param embeddingService 嵌入服务，用于计算向量相似度
     */
    public VectorStore(EmbeddingService embeddingService) {
        this.entries = new CopyOnWriteArrayList<>();
        this.embeddingService = embeddingService;
    }

    // ======================== 写入操作 ========================

    /**
     * 添加一条向量条目.
     *
     * @param id       唯一标识
     * @param vector   向量数据
     * @param text     原始文本内容
     * @param metadata 元数据
     * @throws IllegalArgumentException 如果 ID 已存在
     */
    public void add(String id, float[] vector, String text, Map<String, String> metadata) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID 不能为空");
        }
        // 检查 ID 是否冲突
        for (VectorEntry entry : entries) {
            if (entry.getId().equals(id)) {
                throw new IllegalArgumentException("向量条目 ID 已存在: " + id);
            }
        }
        VectorEntry entry = new VectorEntry(id, vector, text, metadata);
        entries.add(entry);
        log.debug("添加向量条目: id={}, dimension={}", id, vector != null ? vector.length : 0);
    }

    /**
     * 批量添加向量条目.
     *
     * @param newEntries 条目列表
     */
    public void addAll(List<VectorEntry> newEntries) {
        if (newEntries != null) {
            entries.addAll(newEntries);
            log.debug("批量添加向量条目: count={}", newEntries.size());
        }
    }

    // ======================== 查询操作 ========================

    /**
     * 使用余弦相似度搜索最相似的 K 条记录.
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 搜索结果列表（按相似度降序排列）
     */
    public List<SearchResult> search(float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return new ArrayList<>();
        }
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算所有条目的相似度
        List<SearchResult> results = new ArrayList<>();
        for (VectorEntry entry : entries) {
            double score = embeddingService.similarity(queryVector, entry.getVector());
            results.add(new SearchResult(entry.getId(), entry.getText(), score, entry.getMetadata()));
        }

        // 按相似度降序排序，取前 topK
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        log.debug("向量搜索完成: query dimension={}, total entries={}, results={}",
                queryVector.length, entries.size(), results.size());
        return results;
    }

    /**
     * 搜索最相似的 K 条记录（使用文本查询）.
     *
     * <p>先将文本转为向量，然后执行向量搜索.</p>
     *
     * @param queryText 查询文本
     * @param topK      返回数量
     * @return 搜索结果列表
     */
    public List<SearchResult> searchByText(String queryText, int topK) {
        float[] queryVector = embeddingService.embed(queryText);
        return search(queryVector, topK);
    }

    // ======================== 删除操作 ========================

    /**
     * 根据 ID 删除一条向量条目.
     *
     * @param id 要删除的条目 ID
     * @return {@code true} 如果找到并删除，{@code false} 如果 ID 不存在
     */
    public boolean delete(String id) {
        boolean removed = entries.removeIf(entry -> entry.getId().equals(id));
        if (removed) {
            log.debug("删除向量条目: id={}", id);
        }
        return removed;
    }

    /**
     * 清空所有向量条目.
     */
    public void clear() {
        entries.clear();
        log.debug("清空向量存储");
    }

    // ======================== 统计信息 ========================

    /**
     * 获取存储的条目数量.
     *
     * @return 条目数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 检查是否为空.
     *
     * @return {@code true} 如果无条目
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 获取所有条目（深拷贝）.
     *
     * @return 条目列表的副本
     */
    public List<VectorEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }

    // ======================== 内部类：VectorEntry ========================

    /**
     * 向量条目 —— 存储在向量数据库中的一条记录.
     */
    public static class VectorEntry {

        /** 唯一标识 */
        private String id;

        /** 向量数据 */
        private float[] vector;

        /** 原始文本 */
        private String text;

        /** 元数据 */
        private Map<String, String> metadata;

        // ======================== 构造方法 ========================

        /**
         * 创建向量条目.
         *
         * @param id       唯一标识
         * @param vector   向量数据
         * @param text     原始文本
         * @param metadata 元数据
         */
        public VectorEntry(String id, float[] vector, String text, Map<String, String> metadata) {
            this.id = id;
            this.vector = vector;
            this.text = text;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        // ======================== getter / setter ========================

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public float[] getVector() { return vector; }
        public void setVector(float[] vector) { this.vector = vector; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    // ======================== 内部类：SearchResult ========================

    /**
     * 搜索结果 —— 向量搜索返回的一条匹配记录.
     */
    public static class SearchResult {

        /** 条目 ID */
        private String id;

        /** 原始文本 */
        private String text;

        /** 相似度分数（越高越相似） */
        private double score;

        /** 元数据 */
        private Map<String, String> metadata;

        // ======================== 构造方法 ========================

        /**
         * 创建搜索结果.
         *
         * @param id       条目 ID
         * @param text     原始文本
         * @param score    相似度分数
         * @param metadata 元数据
         */
        public SearchResult(String id, String text, double score, Map<String, String> metadata) {
            this.id = id;
            this.text = text;
            this.score = score;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        // ======================== getter / setter ========================

        /**
         * 获取条目 ID.
         *
         * @return 唯一标识
         */
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        /**
         * 获取原始文本内容.
         *
         * @return 原始文本
         */
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        /**
         * 获取相似度分数.
         *
         * @return 余弦相似度值（介于 -1 到 1 之间，越高越相似）
         */
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
}
