package com.github.leyland.letool.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块器 —— 将长文本分割为适合嵌入模型处理的小块.
 *
 * <h3>分块策略</h3>
 * <p>采用分层递归分割策略：</p>
 * <ol>
 *   <li>按段落分割（双换行符）</li>
 *   <li>按句子分割（句号、问号、感叹号等）</li>
 *   <li>按固定大小分割（兜底策略）</li>
 * </ol>
 *
 * <h3>重叠机制</h3>
 * <p>相邻块之间保留一定重叠（默认 50 字符），确保上下文不丢失.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * TextSplitter splitter = new TextSplitter(500, 50);
 *
 * // 分割文本
 * List<String> chunks = splitter.split(longText);
 *
 * // 分割文档列表
 * List<String> chunks = splitter.splitDocuments(documents);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class TextSplitter {

    private static final Logger log = LoggerFactory.getLogger(TextSplitter.class);

    // ======================== 常量 ========================

    /** 默认分块大小（字符数） */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认重叠大小（字符数） */
    public static final int DEFAULT_OVERLAP = 50;

    /** 最小允许的分块大小 */
    public static final int MIN_CHUNK_SIZE = 50;

    /** 句子分隔正则 */
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
            "(?<=[。！？.!?])\\s*");

    /** 段落分隔正则 */
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "\\n\\s*\\n");

    // ======================== 字段 ========================

    /** 分块大小（字符数） */
    private final int chunkSize;

    /** 相邻块重叠字符数 */
    private final int overlap;

    // ======================== 构造方法 ========================

    /**
     * 使用默认参数创建文本分块器（chunkSize=500, overlap=50）.
     */
    public TextSplitter() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 创建文本分块器.
     *
     * @param chunkSize 分块大小（字符数），不能小于 {@value #MIN_CHUNK_SIZE}
     * @param overlap   重叠字符数，不能大于 chunkSize
     */
    public TextSplitter(int chunkSize, int overlap) {
        if (chunkSize < MIN_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize 不能小于 " + MIN_CHUNK_SIZE);
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 必须小于 chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        log.debug("创建 TextSplitter: chunkSize={}, overlap={}", chunkSize, overlap);
    }

    // ======================== 核心分割方法 ========================

    /**
     * 将文本分割为重叠的小块.
     *
     * <p>分割顺序：段落 -> 句子 -> 固定大小.</p>
     *
     * @param text 原始文本
     * @return 文本块列表
     */
    public List<String> split(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 步骤 1：尝试按段落分割
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        List<String> chunks = new ArrayList<>();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() <= chunkSize) {
                // 段落本身足够短
                chunks.add(trimmed);
            } else {
                // 步骤 2：尝试按句子分割
                String[] sentences = SENTENCE_PATTERN.split(trimmed);
                StringBuilder current = new StringBuilder();

                for (String sentence : sentences) {
                    String s = sentence.trim();
                    if (s.isEmpty()) {
                        continue;
                    }

                    if (current.length() + s.length() <= chunkSize) {
                        // 追加到当前块
                        if (current.length() > 0) {
                            current.append(" ");
                        }
                        current.append(s);
                    } else {
                        // 当前块已满，保存并开始新块
                        if (current.length() > 0) {
                            chunks.add(current.toString().trim());
                        }

                        // 步骤 3：如果单个句子仍超过 chunkSize，按固定大小分割
                        if (s.length() > chunkSize) {
                            chunks.addAll(splitBySize(s));
                            current = new StringBuilder();
                        } else {
                            current = new StringBuilder(s);
                        }
                    }
                }

                // 保存最后一个块
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                }
            }
        }

        // 步骤 4：合并过小的块
        chunks = mergeSmallChunks(chunks);

        // 步骤 5：添加重叠
        if (overlap > 0 && chunks.size() > 1) {
            chunks = addOverlap(chunks);
        }

        log.debug("文本分割完成: original length={}, chunks={}", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 批量分割多个文档.
     *
     * @param documents 文档列表
     * @return 所有文档的文本块列表
     */
    public List<String> splitDocuments(List<DocumentLoader.Document> documents) {
        List<String> allChunks = new ArrayList<>();
        for (DocumentLoader.Document doc : documents) {
            List<String> chunks = split(doc.getContent());
            // 将来源信息附加到每个块
            for (String chunk : chunks) {
                allChunks.add("[来源: " + doc.getSource() + "] " + chunk);
            }
        }
        log.info("批量文档分割完成: documents={}, total chunks={}", documents.size(), allChunks.size());
        return allChunks;
    }

    // ======================== 内部方法 ========================

    /**
     * 按固定大小分割文本（兜底策略）.
     *
     * @param text 超长文本
     * @return 固定大小的文本块
     */
    private List<String> splitBySize(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // 尝试在空格处截断
            if (end < text.length()) {
                int spaceIndex = text.lastIndexOf(' ', end);
                if (spaceIndex > start) {
                    end = spaceIndex;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    /**
     * 合并过小的文本块.
     *
     * <p>将小于 chunkSize/2 的块与相邻块合并.</p>
     *
     * @param chunks 原始文本块
     * @return 合并后的文本块
     */
    private List<String> mergeSmallChunks(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        int minSize = chunkSize / 2;
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String chunk : chunks) {
            if (buffer.length() + chunk.length() <= chunkSize) {
                if (buffer.length() > 0) {
                    buffer.append(" ");
                }
                buffer.append(chunk);
            } else {
                if (buffer.length() > 0) {
                    merged.add(buffer.toString().trim());
                }
                buffer = new StringBuilder(chunk);
            }
        }

        if (buffer.length() > 0) {
            merged.add(buffer.toString().trim());
        }

        return merged;
    }

    /**
     * 为相邻块添加重叠内容.
     *
     * @param chunks 原始文本块
     * @return 带重叠的文本块
     */
    private List<String> addOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String current = chunks.get(i);

            if (i < chunks.size() - 1) {
                String next = chunks.get(i + 1);
                // 从下一块的开头取 overlap 个字符追加到当前块
                int overlapLen = Math.min(overlap, next.length());
                String overlapText = next.substring(0, overlapLen);
                result.add(current + " " + overlapText);
            } else {
                result.add(current);
            }
        }

        return result;
    }
}
