package com.github.leyland.letool.rule.chain;

import com.github.leyland.letool.rule.exception.RuleException;
import com.github.leyland.letool.tool.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则链解析器 —— 负责将 YAML/JSON 格式的规则定义文本解析为 {@link ChainDefinition} 对象.
 *
 * <h3>支持格式</h3>
 * <ul>
 *   <li><b>单文档 YAML</b> —— 单个规则链定义</li>
 *   <li><b>多文档 YAML</b> —— 以 {@code ---} 分隔的多个规则链定义</li>
 *   <li><b>JSON</b> —— 单个规则链的 JSON 表示</li>
 * </ul>
 *
 * <h3>YAML 示例</h3>
 * <pre>{@code
 * name: risk-evaluation
 * description: 风险评估规则链
 * nodes:
 *   - name: dataCollector
 *     type: THEN
 *     children:
 *       - name: scoreCalculator
 *       - name: riskClassifier
 *   - name: reportGenerator
 *     type: THEN
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ChainDefinition
 * @see ChainManager
 */
public class ChainParser {

    private static final Logger log = LoggerFactory.getLogger(ChainParser.class);

    // ======================== YAML 解析 ========================

    /**
     * 解析单个 YAML 字符串为规则链定义.
     *
     * @param yaml YAML 格式的规则链定义文本
     * @return 解析后的规则链定义
     * @throws RuleException 解析失败时抛出
     */
    public ChainDefinition parseYaml(String yaml) {
        if (yaml == null || yaml.trim().isEmpty()) {
            throw new RuleException("PARSE_001", "规则链 YAML 内容不能为空");
        }
        try {
            Yaml yamlParser = new Yaml();
            return yamlParser.loadAs(yaml, ChainDefinition.class);
        } catch (YAMLException e) {
            log.error("解析规则链 YAML 失败: {}", e.getMessage(), e);
            throw new RuleException("PARSE_002", "规则链 YAML 解析失败: " + e.getMessage(), e);
        }
    }

    // ======================== JSON 解析 ========================

    /**
     * 解析 JSON 字符串为规则链定义.
     *
     * @param json JSON 格式的规则链定义文本
     * @return 解析后的规则链定义
     * @throws RuleException 解析失败时抛出
     */
    public ChainDefinition parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new RuleException("PARSE_003", "规则链 JSON 内容不能为空");
        }
        try {
            return JsonUtil.parseObject(json, ChainDefinition.class);
        } catch (Exception e) {
            log.error("解析规则链 JSON 失败: {}", e.getMessage(), e);
            throw new RuleException("PARSE_004", "规则链 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    // ======================== 文件解析 ========================

    /**
     * 解析文件中的规则链定义（支持多文档 YAML 和单文档）.
     *
     * <p>如果文件内容包含 {@code ---} 分隔符，则解析为多个规则链定义；
     * 否则解析为单个规则链定义.</p>
     *
     * @param filePath 文件路径（支持绝对路径和 classpath: 前缀）
     * @return 解析出的规则链定义列表，如果文件不存在或格式错误则返回空列表
     */
    public List<ChainDefinition> parseFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ChainDefinition> chains = new ArrayList<>();
        String content;

        try {
            content = readFileContent(filePath);
        } catch (IOException e) {
            log.error("读取规则链文件失败: {}", filePath, e);
            return Collections.emptyList();
        }

        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 检测是否为多文档 YAML
        if (content.contains("\n---\n") || content.startsWith("---")) {
            // 多文档 YAML 解析
            Yaml yaml = new Yaml();
            Iterable<Object> documents = yaml.loadAll(content);

            for (Object doc : documents) {
                if (doc instanceof Map) {
                    try {
                        // 重新序列化单个文档并解析
                        String singleYaml = new Yaml().dump(doc);
                        ChainDefinition chain = parseYaml(singleYaml);
                        chains.add(chain);
                    } catch (Exception e) {
                        log.warn("解析多文档 YAML 中的一个文档失败: {}", e.getMessage());
                    }
                }
            }
        } else {
            // 单文档解析
            try {
                ChainDefinition chain = parseYaml(content);
                chains.add(chain);
            } catch (Exception e) {
                // 尝试 JSON 解析
                try {
                    ChainDefinition chain = parseJson(content);
                    chains.add(chain);
                } catch (Exception ex) {
                    log.error("文件 {} 既不是有效的 YAML 也不是有效的 JSON: {}", filePath, ex.getMessage());
                }
            }
        }

        return chains;
    }

    /**
     * 解析目录下的所有 YAML 规则链文件.
     *
     * @param directoryPath 目录路径
     * @return 解析出的所有规则链定义列表
     */
    public List<ChainDefinition> parseDirectory(String directoryPath) {
        List<ChainDefinition> allChains = new ArrayList<>();

        try {
            Path dirPath = resolvePath(directoryPath);
            if (!Files.isDirectory(dirPath)) {
                log.warn("规则链目录不存在或不是目录: {}", directoryPath);
                return allChains;
            }

            List<Path> yamlFiles = Files.list(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .collect(Collectors.toList());

            for (Path yamlFile : yamlFiles) {
                List<ChainDefinition> chains = parseFile(yamlFile.toString());
                allChains.addAll(chains);
            }

            log.info("从目录 {} 加载了 {} 个规则链定义（{} 个文件）",
                    directoryPath, allChains.size(), yamlFiles.size());

        } catch (IOException e) {
            log.error("扫描规则链目录失败: {}", directoryPath, e);
        }

        return allChains;
    }

    // ======================== 文件读取辅助方法 ========================

    /**
     * 读取文件内容，支持 classpath: 前缀.
     *
     * @param filePath 文件路径
     * @return 文件文本内容
     * @throws IOException 读取失败时抛出
     */
    private String readFileContent(String filePath) throws IOException {
        // classpath 资源
        if (filePath.startsWith("classpath:")) {
            String resourcePath = filePath.substring("classpath:".length());
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = ChainParser.class.getClassLoader();
            }
            try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("classpath 资源不存在: " + resourcePath);
                }
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        // 文件系统
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + filePath);
        }
        return Files.readString(file.toPath());
    }

    /**
     * 解析路径（支持 classpath: 前缀）为文件系统路径.
     *
     * @param path 原始路径
     * @return 文件系统路径
     * @throws IOException 路径无法解析时抛出
     */
    private Path resolvePath(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = ChainParser.class.getClassLoader();
            }
            var resource = classLoader.getResource(resourcePath);
            if (resource == null) {
                throw new IOException("classpath 资源不存在: " + resourcePath);
            }
            try {
                return Paths.get(resource.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("资源路径 URI 格式错误: " + resourcePath, e);
            }
        }
        return Paths.get(path);
    }
}
