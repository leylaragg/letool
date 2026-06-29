package com.github.leyland.letool.ai.embedding;

import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.core.EmbeddingResponse;
import com.github.leyland.letool.ai.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 嵌入服务 —— 提供文本向量化的高级封装和向量运算工具.
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>单文本嵌入：将一段文本转换为浮点向量</li>
 *   <li>批量嵌入：一次将多条文本转换为向量（减少 API 调用次数）</li>
 *   <li>余弦相似度计算：比较两个向量的语义相似度</li>
 *   <li>最相似搜索：在一组候选向量中查找与查询向量最相似的</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private EmbeddingService embeddingService;
 *
 * // 单文本嵌入
 * float[] vec = embeddingService.embed("你好世界");
 *
 * // 批量嵌入
 * List<float[]> vecs = embeddingService.embed(List.of("苹果", "香蕉", "橙子"));
 *
 * // 相似度计算
 * double sim = embeddingService.similarity(vec1, vec2);
 *
 * // 找最相似
 * int idx = embeddingService.findMostSimilar(queryVec, candidateVecs);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    // ======================== 字段 ========================

    /** AI 模板引擎 */
    private final AiTemplate aiTemplate;

    // ======================== 构造方法 ========================

    /**
     * 创建嵌入服务实例.
     *
     * @param aiTemplate AI 模板引擎
     */
    public EmbeddingService(AiTemplate aiTemplate) {
        this.aiTemplate = aiTemplate;
    }

    // ======================== 嵌入方法 ========================

    /**
     * 单文本嵌入 —— 将一段文本转换为向量.
     *
     * @param text 待嵌入的文本
     * @return 浮点向量数组
     * @throws AiException 当 API 调用失败时
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }
        float[] result = aiTemplate.embedding()
                .input(text)
                .executeSingle();
        log.debug("单文本嵌入完成: text length={}, vector dimension={}", text.length(),
                result != null ? result.length : 0);
        return result;
    }

    /**
     * 批量嵌入 —— 将多条文本转换为向量.
     *
     * <p>一次性发送所有文本到 API，比多次调用 {@link #embed(String)} 更高效.</p>
     *
     * @param texts 待嵌入的文本列表
     * @return 向量列表，顺序与输入一致
     * @throws AiException 当 API 调用失败时
     */
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        EmbeddingResponse response = aiTemplate.embedding()
                .input(texts)
                .execute();

        List<float[]> result = new ArrayList<>();
        if (response.getData() != null) {
            for (EmbeddingResponse.EmbeddingData data : response.getData()) {
                result.add(data.getEmbedding());
            }
        }
        log.debug("批量嵌入完成: input count={}, output count={}", texts.size(), result.size());
        return result;
    }

    // ======================== 向量运算 ========================

    /**
     * 计算两个向量的余弦相似度.
     *
     * <p>余弦相似度取值范围 [-1, 1]，值越接近 1 表示语义越相似.</p>
     *
     * <p>计算公式：cos(θ) = (A · B) / (||A|| * ||B||)</p>
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 余弦相似度 [-1, 1]
     * @throws IllegalArgumentException 如果向量维度不匹配或为空
     */
    public double similarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    String.format("向量维度不匹配: a=%d, b=%d", a.length, b.length));
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 在一组候选向量中查找与查询向量最相似的.
     *
     * <p>返回相似度最高的候选向量在列表中的索引.</p>
     *
     * @param query      查询向量
     * @param candidates 候选向量列表
     * @return 最相似候选向量的索引，如果候选列表为空返回 -1
     */
    public int findMostSimilar(float[] query, List<float[]> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return -1;
        }

        int bestIndex = 0;
        double bestScore = similarity(query, candidates.get(0));

        for (int i = 1; i < candidates.size(); i++) {
            double score = similarity(query, candidates.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * 在一组候选向量中查找与查询向量最相似的 K 个（返回索引）.
     *
     * @param query      查询向量
     * @param candidates 候选向量列表
     * @param topK       返回数量
     * @return 最相似候选向量索引列表（按相似度降序排列）
     */
    public List<Integer> findTopK(float[] query, List<float[]> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        return java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(i -> new AbstractMap.SimpleEntry<>(i, similarity(query, candidates.get(i))))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
