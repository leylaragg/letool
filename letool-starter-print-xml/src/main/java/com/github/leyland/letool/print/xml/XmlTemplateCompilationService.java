package com.github.leyland.letool.print.xml;

import com.github.leyland.letool.print.api.OutputFormat;
import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.exception.PrintValidationException;
import com.github.leyland.letool.print.template.TemplateCompilationKey;
import com.github.leyland.letool.print.template.TemplateDefinition;
import com.github.leyland.letool.print.template.TemplateRepository;
import com.github.leyland.letool.print.template.TemplateSet;

import java.util.Arrays;
import java.util.Objects;

/**
 * 从模板仓库锁定版本快照，并解析可复用的 XML 编译结果。
 *
 * <p>服务本身不保存可变状态，可与线程安全的仓库和缓存一起并发使用。</p>
 *
 * @author leyland
 */
public final class XmlTemplateCompilationService {

    /** 提供已发布模板集合快照的仓库。 */
    private final TemplateRepository repository;

    /** 复用集合和文档编译结果的本地缓存。 */
    private final XmlTemplateCompilationCache cache;

    /**
     * 创建运行时 XML 模板解析服务。
     *
     * @param repository 模板集合仓库
     * @param cache 可与发布校验共享的编译缓存
     * @throws NullPointerException 任一依赖为空时抛出
     */
    public XmlTemplateCompilationService(
            TemplateRepository repository, XmlTemplateCompilationCache cache) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.cache = Objects.requireNonNull(cache, "cache 不能为空");
    }

    /**
     * 解析宿主明确指定的模板集合版本。
     *
     * @param templateSetVersion 模板集合版本
     * @param templateCode 模板代码
     * @param rendererProfileVersion 渲染器配置版本
     * @param outputFormat 目标输出格式
     * @return 已锁定完整编译条件的 XML 模板
     * @throws PrintValidationException 集合版本、模板或元数据无效时抛出
     * @throws PrintCompilationException XML 集合编译失败时抛出
     * @throws IllegalArgumentException 编译版本条件不合法时抛出
     * @throws NullPointerException 模板代码或输出格式为空时抛出
     */
    public ResolvedXmlTemplate resolve(
            long templateSetVersion,
            String templateCode,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        TemplateSet templateSet = repository.find(templateSetVersion)
                .orElseThrow(() -> PrintValidationException.invalidRequest(
                        "模板集合版本尚未发布：" + templateSetVersion));
        return resolveSnapshot(templateSet, templateCode, rendererProfileVersion, outputFormat);
    }

    /**
     * 解析请求已经锁定的模板快照，并与仓库中的同版本定义重新核对。
     *
     * <p>调用方携带的模板内容不能替代仓库快照，这个入口只在两者完全一致后复用编译缓存。</p>
     *
     * @param template 请求携带的不可变模板快照
     * @param rendererProfileVersion 渲染器配置版本
     * @param outputFormat 目标输出格式
     * @return 已锁定完整编译条件的 XML 模板
     * @throws PrintValidationException 集合不存在或模板快照不一致时抛出
     * @throws PrintCompilationException XML 集合编译失败时抛出
     * @throws IllegalArgumentException 编译版本条件不合法时抛出
     * @throws NullPointerException 模板或输出格式为空时抛出
     */
    public ResolvedXmlTemplate resolve(
            PrintTemplate template,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        Objects.requireNonNull(template, "template 不能为空");
        TemplateSet templateSet = repository.find(template.templateSetVersion())
                .orElseThrow(() -> PrintValidationException.invalidRequest(
                        "模板集合版本尚未发布：" + template.templateSetVersion()));
        PrintTemplate stored = templateSet.require(template.templateCode()).template();
        if (!sameSnapshot(stored, template)) {
            throw PrintValidationException.invalidRequest("请求模板与仓库模板快照不一致");
        }
        return resolveSnapshot(
                templateSet, template.templateCode(), rendererProfileVersion, outputFormat);
    }

    /**
     * 解析仓库当前激活的模板集合快照。
     *
     * @param templateCode 模板代码
     * @param rendererProfileVersion 渲染器配置版本
     * @param outputFormat 目标输出格式
     * @return 已锁定完整编译条件的 XML 模板
     * @throws PrintValidationException 当前集合、模板或元数据无效时抛出
     * @throws PrintCompilationException XML 集合编译失败时抛出
     * @throws IllegalArgumentException 编译版本条件不合法时抛出
     * @throws NullPointerException 模板代码或输出格式为空时抛出
     */
    public ResolvedXmlTemplate resolveCurrent(
            String templateCode,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        TemplateSet templateSet = repository.current()
                .orElseThrow(() -> PrintValidationException.invalidRequest("当前没有已激活的模板集合"));
        return resolveSnapshot(templateSet, templateCode, rendererProfileVersion, outputFormat);
    }

    /**
     * 从单次仓库读取的快照构造完整编译键。
     *
     * @param templateSet 本次调用锁定的集合快照
     * @param templateCode 目标模板代码
     * @param rendererProfileVersion 渲染器配置版本
     * @param outputFormat 目标输出格式
     * @return 缓存解析结果
     */
    private ResolvedXmlTemplate resolveSnapshot(
            TemplateSet templateSet,
            String templateCode,
            long rendererProfileVersion,
            OutputFormat outputFormat) {
        TemplateDefinition definition = templateSet.require(templateCode);
        PrintTemplate template = definition.template();
        TemplateCompilationKey key = new TemplateCompilationKey(
                templateSet.version(), templateSet.digest(), template.templateCode(),
                template.dslVersion(), template.contextVersion(), rendererProfileVersion, outputFormat);
        return cache.resolve(templateSet, key);
    }

    /** 比较所有影响编译和绑定语义的模板字段。 */
    private boolean sameSnapshot(PrintTemplate stored, PrintTemplate requested) {
        return stored.templateCode().equals(requested.templateCode())
                && stored.templateFormat().equals(requested.templateFormat())
                && stored.dslVersion() == requested.dslVersion()
                && stored.templateSetVersion() == requested.templateSetVersion()
                && stored.contextVersion() == requested.contextVersion()
                && Arrays.equals(stored.content(), requested.content());
    }
}
