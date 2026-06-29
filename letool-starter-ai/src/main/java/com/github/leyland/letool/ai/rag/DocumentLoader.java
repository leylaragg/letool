package com.github.leyland.letool.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文档加载器 —— 从文件系统加载文本和 PDF 文档.
 *
 * <h3>支持的格式</h3>
 * <ul>
 *   <li><b>.txt</b> —— 纯文本文件</li>
 *   <li><b>.md</b> —— Markdown 文件</li>
 *   <li><b>.json</b> —— JSON 文件</li>
 *   <li><b>.csv</b> —— CSV 文件</li>
 *   <li><b>.pdf</b> —— PDF 文件（基础支持，提取纯文本）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DocumentLoader loader = new DocumentLoader();
 *
 * // 加载单个文件
 * List<Document> docs = loader.loadFromFile("/data/knowledge.txt");
 *
 * // 加载整个目录
 * List<Document> docs = loader.loadFromDirectory("/data/knowledge/");
 *
 * // 从文本创建
 * Document doc = loader.loadFromText("这是一段知识文本", "manual");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    // ======================== 文件加载 ========================

    /**
     * 从单个文件加载文档内容.
     *
     * <p>自动检测文件扩展名，选择合适的解析方式.</p>
     *
     * @param filePath 文件路径
     * @return 文档列表（大文件可能在内部被分割为多个文档）
     * @throws IOException 当文件读取失败时
     */
    public List<Document> loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + filePath);
        }

        String fileName = path.getFileName().toString();
        String extension = getExtension(fileName).toLowerCase();

        log.debug("加载文件: path={}, extension={}", filePath, extension);

        switch (extension) {
            case "pdf":
                return loadPdf(path);
            case "txt":
            case "md":
            case "csv":
            case "json":
            case "xml":
            case "html":
                return loadText(path);
            default:
                // 尝试以文本方式读取
                return loadText(path);
        }
    }

    /**
     * 从目录加载所有支持的文档.
     *
     * <p>递归遍历目录，加载所有 .txt、.md、.json、.csv、.pdf 文件.</p>
     *
     * @param directoryPath 目录路径
     * @return 所有文件内容的文档列表
     * @throws IOException 当目录读取失败时
     */
    public List<Document> loadFromDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.isDirectory(dir)) {
            throw new IOException("目录不存在或不是目录: " + directoryPath);
        }

        List<Document> allDocs = new ArrayList<>();
        Set<String> supportedExtensions = new HashSet<>(Arrays.asList(
                "txt", "md", "csv", "json", "pdf", "xml", "html", "htm"));

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String ext = getExtension(file.getFileName().toString()).toLowerCase();
                if (supportedExtensions.contains(ext)) {
                    try {
                        allDocs.addAll(loadFromFile(file.toString()));
                    } catch (IOException e) {
                        log.warn("加载文件失败: {}", file, e);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("从目录加载文档完成: path={}, document count={}", directoryPath, allDocs.size());
        return allDocs;
    }

    /**
     * 从纯文本创建文档.
     *
     * @param text   文本内容
     * @param source 来源标识（用于引用和溯源）
     * @return 包含单个 Document 的列表
     */
    public List<Document> loadFromText(String text, String source) {
        Document doc = new Document(text, source);
        return Collections.singletonList(doc);
    }

    /**
     * 从多段文本创建文档列表.
     *
     * @param texts  文本列表
     * @param source 来源标识
     * @return 文档列表
     */
    public List<Document> loadFromTexts(List<String> texts, String source) {
        return texts.stream()
                .map(text -> new Document(text, source))
                .collect(Collectors.toList());
    }

    // ======================== 内部解析方法 ========================

    /**
     * 以 UTF-8 编码读取文本文件.
     *
     * @param path 文件路径
     * @return 文档列表
     * @throws IOException 当文件读取失败时
     */
    private List<Document> loadText(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return Collections.singletonList(new Document(content, path.toString()));
    }

    /**
     * 基础 PDF 文本提取（使用简单的文本提取策略）.
     *
     * <p>完整 PDF 解析需引入 PDFBox 等第三方库.
     * 当前实现尝试将 PDF 作为二进制读取并提取可读文本.</p>
     *
     * @param path PDF 文件路径
     * @return 文档列表
     * @throws IOException 当文件读取失败时
     */
    private List<Document> loadPdf(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        // 基础方法：提取字节中的可读 ASCII/UTF-8 片段
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (b >= 32 && b < 127 || b == '\n' || b == '\r' || b == '\t') {
                sb.append((char) b);
            }
        }
        String text = sb.toString();
        // 基础的去噪
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        text = text.replaceAll("\\s{3,}", "\n\n");

        if (text.trim().isEmpty()) {
            log.warn("PDF 提取文本为空，建议引入 PDFBox 或 iText 库以获得更好的解析效果: {}", path);
            return Collections.emptyList();
        }

        return Collections.singletonList(new Document(text.trim(), path.toString()));
    }

    // ======================== 工具方法 ========================

    /**
     * 获取文件扩展名（不含点号）.
     *
     * @param fileName 文件名
     * @return 扩展名（小写），无扩展名返回空串
     */
    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    // ======================== 内部类：Document ========================

    /**
     * 文档模型 —— 表示加载到 RAG 系统中的一份文本内容.
     *
     * <p>包含文本内容、来源路径和可选的元数据（作者、创建时间等）.</p>
     */
    public static class Document {

        /** 文档内容 */
        private String content;

        /** 文档来源（如文件路径、URL） */
        private String source;

        /** 文档元数据（如作者、创建时间、标签等） */
        private Map<String, String> metadata;

        // ======================== 构造方法 ========================

        /**
         * 创建文档.
         *
         * @param content 文档内容
         * @param source  来源标识
         */
        public Document(String content, String source) {
            this.content = content;
            this.source = source;
            this.metadata = new HashMap<>();
        }

        /**
         * 创建带元数据的文档.
         *
         * @param content  文档内容
         * @param source   来源标识
         * @param metadata 元数据
         */
        public Document(String content, String source, Map<String, String> metadata) {
            this.content = content;
            this.source = source;
            this.metadata = new HashMap<>(metadata);
        }

        // ======================== getter / setter ========================

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

        /**
         * 添加元数据.
         *
         * @param key   键
         * @param value 值
         */
        public void addMetadata(String key, String value) {
            this.metadata.put(key, value);
        }

        /**
         * 获取元数据.
         *
         * @param key 键
         * @return 值，不存在返回 {@code null}
         */
        public String getMetadata(String key) {
            return this.metadata.get(key);
        }
    }
}
