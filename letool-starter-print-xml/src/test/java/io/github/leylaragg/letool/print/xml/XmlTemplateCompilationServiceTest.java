package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML 模板版本解析与编译服务测试。
 *
 * @author leyland
 */
class XmlTemplateCompilationServiceTest {

    /** 模拟宿主扩展提供的输出格式，验证服务不会收窄格式边界。 */
    private static final OutputFormat HTML = new OutputFormat("html", "text/html", "html");

    /** 显式版本解析只读取一次仓库，并从模板元数据构造编译键。 */
    @Test
    void shouldResolveExplicitVersionWithSingleRepositoryLookup() {
        CountingTemplateRepository repository = repositoryWithActiveSet();
        XmlTemplateCompilationService service = service(repository);

        ResolvedXmlTemplate resolved = service.resolve(7, "main", 3, OutputFormat.PDF);

        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(repository.currentCalls).isZero();
        assertThat(resolved.key().templateSetVersion()).isEqualTo(7);
        assertThat(resolved.key().templateSetDigest()).hasSize(64);
        assertThat(resolved.key().rendererProfileVersion()).isEqualTo(3);
        assertThat(resolved.key().outputFormat()).isEqualTo(OutputFormat.PDF);
    }

    /** 当前版本解析只获取一次激活快照，不在调用过程中二次追踪指针。 */
    @Test
    void shouldResolveCurrentVersionWithSingleRepositoryLookup() {
        CountingTemplateRepository repository = repositoryWithActiveSet();
        XmlTemplateCompilationService service = service(repository);

        ResolvedXmlTemplate resolved = service.resolveCurrent("main", 4, HTML);

        assertThat(repository.currentCalls).isEqualTo(1);
        assertThat(repository.findCalls).isZero();
        assertThat(resolved.key().templateSetVersion()).isEqualTo(7);
        assertThat(resolved.key().outputFormat()).isEqualTo(HTML);
    }

    /** 请求已经锁定模板时，服务仍需回到同版本仓库核对完整快照。 */
    @Test
    void shouldResolveMatchingRequestSnapshotWithSingleRepositoryLookup() {
        CountingTemplateRepository repository = repositoryWithActiveSet();
        XmlTemplateCompilationService service = service(repository);

        ResolvedXmlTemplate resolved = service.resolve(
                repository.template("main"), 5, OutputFormat.PDF);

        assertThat(repository.findCalls).isEqualTo(1);
        assertThat(repository.currentCalls).isZero();
        assertThat(resolved.key().rendererProfileVersion()).isEqualTo(5);
    }

    /** 文档声明输出白名单时，解析服务应在绑定前拒绝未授权格式。 */
    @Test
    void shouldEnforceDeclaredOutputWhitelist() {
        CountingTemplateRepository repository = repositoryWithActiveSet(" outputs=\"pdf\"");
        XmlTemplateCompilationService service = service(repository);

        assertThat(service.resolveCurrent("main", 1, OutputFormat.PDF)).isNotNull();
        assertThatThrownBy(() -> service.resolveCurrent("main", 1, HTML))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("输出格式")
                .hasMessageContaining("html");
    }

    /** 相同版本和代码不能掩盖正文或编译元数据被调用方替换。 */
    @Test
    void shouldRejectRequestSnapshotDifferentFromRepository() {
        CountingTemplateRepository repository = repositoryWithActiveSet();
        XmlTemplateCompilationService service = service(repository);
        PrintTemplate stored = repository.template("main");
        PrintTemplate changedContent = new PrintTemplate(
                stored.templateCode(), stored.templateFormat(), stored.dslVersion(),
                stored.templateSetVersion(), stored.contextVersion(), "changed".getBytes(StandardCharsets.UTF_8));
        PrintTemplate changedFormat = new PrintTemplate(
                stored.templateCode(), new TemplateFormat("other-template"), stored.dslVersion(),
                stored.templateSetVersion(), stored.contextVersion(), stored.content());
        PrintTemplate changedDsl = new PrintTemplate(
                stored.templateCode(), stored.templateFormat(), 2,
                stored.templateSetVersion(), stored.contextVersion(), stored.content());
        PrintTemplate changedContext = new PrintTemplate(
                stored.templateCode(), stored.templateFormat(), stored.dslVersion(),
                stored.templateSetVersion(), 2, stored.content());

        assertThatThrownBy(() -> service.resolve(changedContent, 1, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板快照不一致")
                .hasMessageNotContaining("changed");
        assertThatThrownBy(() -> service.resolve(changedFormat, 1, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板快照不一致");
        assertThatThrownBy(() -> service.resolve(changedDsl, 1, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板快照不一致");
        assertThatThrownBy(() -> service.resolve(changedContext, 1, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("模板快照不一致");
    }

    /** 仓库没有目标快照时应返回稳定错误，不尝试临时切换版本。 */
    @Test
    void shouldRejectMissingVersionOrCurrentSet() {
        CountingTemplateRepository repository = new CountingTemplateRepository();
        XmlTemplateCompilationService service = service(repository);

        assertThatThrownBy(() -> service.resolve(7, "main", 3, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("7");
        assertThatThrownBy(() -> service.resolveCurrent("main", 3, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("当前");
    }

    /** 运行时入口只接受可打印的 Letool XML 文档。 */
    @Test
    void shouldRejectMissingOrUnsupportedDocument() {
        CountingTemplateRepository repository = repositoryWithActiveSet();
        XmlTemplateCompilationService service = service(repository);

        assertThatThrownBy(() -> service.resolve(7, "missing", 3, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> service.resolve(7, "shared", 3, OutputFormat.PDF))
                .isInstanceOf(PrintValidationException.class);
    }

    /** 组装测试服务。 */
    private XmlTemplateCompilationService service(TemplateRepository repository) {
        return new XmlTemplateCompilationService(repository,
                new XmlTemplateCompilationCache(new XmlTemplateSetCompiler()));
    }

    /** 创建已经激活版本 7 的计数仓库。 */
    private CountingTemplateRepository repositoryWithActiveSet() {
        return repositoryWithActiveSet("");
    }

    /** 创建带可选 document 属性的已激活测试仓库。 */
    private CountingTemplateRepository repositoryWithActiveSet(String documentAttributes) {
        CountingTemplateRepository repository = new CountingTemplateRepository();
        String source = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"" + documentAttributes
                + "><page><page-body><paragraph>正文</paragraph></page-body></page></document>";
        PrintTemplate template = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                source.getBytes(StandardCharsets.UTF_8));
        new TemplateSetPublisher(repository, List.of()).publishAndActivate(7,
                List.of(
                        new TemplateDefinition(TemplateType.DOCUMENT, template),
                        fragment()));
        repository.resetLookupCounts();
        return repository;
    }

    /** 创建不能直接解析为文档的共享片段。 */
    private TemplateDefinition fragment() {
        String source = "<fragment xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\"><paragraph>共享片段</paragraph></fragment>";
        PrintTemplate template = new PrintTemplate("shared", TemplateFormat.LETOOL_XML, 1, 7, 1,
                source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.FRAGMENT, template);
    }

    /**
     * 记录读取次数的仓库代理，用来确认一次解析只持有一个仓库快照。
     *
     * @author leyland
     */
    private static final class CountingTemplateRepository implements TemplateRepository {

        /** 保存真实版本状态。 */
        private final InMemoryTemplateRepository delegate = new InMemoryTemplateRepository();

        /** 按版本读取次数。 */
        private int findCalls;

        /** 当前版本读取次数。 */
        private int currentCalls;

        @Override
        public Optional<TemplateSet> find(long version) {
            findCalls++;
            return delegate.find(version);
        }

        @Override
        public Optional<TemplateSet> current() {
            currentCalls++;
            return delegate.current();
        }

        @Override
        public TemplateSet publish(TemplateSet templateSet) {
            return delegate.publish(templateSet);
        }

        @Override
        public TemplateSet publishAndActivate(TemplateSet templateSet) {
            return delegate.publishAndActivate(templateSet);
        }

        @Override
        public TemplateSet activate(long version) {
            return delegate.activate(version);
        }

        /** 清除准备数据时产生的读取记录。 */
        private void resetLookupCounts() {
            findCalls = 0;
            currentCalls = 0;
        }

        /** 不计入解析调用次数地读取准备好的模板。 */
        private PrintTemplate template(String templateCode) {
            return delegate.current().orElseThrow().require(templateCode).template();
        }
    }
}
