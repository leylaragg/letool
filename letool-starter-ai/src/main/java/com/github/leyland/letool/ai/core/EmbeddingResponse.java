package com.github.leyland.letool.ai.core;

import java.util.List;

/**
 * 嵌入响应模型 —— 封装文本向量化 API 的返回结果.
 *
 * <h3>数据结构</h3>
 * <p>包含一个 {@link EmbeddingData} 列表，每个元素对应一条输入文本的向量表示，
 * 以及 Token 使用统计.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class EmbeddingResponse {

    // ======================== 字段 ========================

    /** 嵌入数据列表，每个元素对应一条输入文本 */
    private List<EmbeddingData> data;

    /** Token 使用统计 */
    private ChatResponse.Usage usage;

    // ======================== getter / setter ========================

    public List<EmbeddingData> getData() { return data; }
    public void setData(List<EmbeddingData> data) { this.data = data; }
    public ChatResponse.Usage getUsage() { return usage; }
    public void setUsage(ChatResponse.Usage usage) { this.usage = usage; }

    // ======================== 内部类：EmbeddingData ========================

    /**
     * 嵌入数据 —— 单条文本的向量表示.
     *
     * <p>包含原始文本在输入列表中的索引和对应的浮点向量.</p>
     */
    public static class EmbeddingData {

        /** 文本在输入列表中的索引 */
        private int index;

        /** 向量数据（浮点数组） */
        private float[] embedding;

        // ======================== getter / setter ========================

        /**
         * 获取文本索引.
         *
         * @return 输入文本在原始列表中的位置
         */
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        /**
         * 获取向量数据.
         *
         * @return 浮点向量数组
         */
        public float[] getEmbedding() { return embedding; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }

        /**
         * 获取向量维度.
         *
         * @return 向量的长度
         */
        public int getDimension() {
            return embedding != null ? embedding.length : 0;
        }
    }
}
