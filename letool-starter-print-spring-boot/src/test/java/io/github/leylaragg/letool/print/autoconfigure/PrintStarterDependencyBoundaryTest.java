package io.github.leylaragg.letool.print.autoconfigure;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印主 Starter 的 Maven 依赖方向测试。
 *
 * @author leyland
 */
class PrintStarterDependencyBoundaryTest {

    /** 主 Starter 不应把业务模块、规则引擎、任务调度或 JasperReports 带进来。 */
    @Test
    void shouldKeepStarterDependenciesGeneralAndSpelOptional() throws Exception {
        Path module = Path.of(System.getProperty("basedir"));
        List<Dependency> dependencies = dependencies(module.resolve("pom.xml"));

        assertThat(dependencies).extracting(Dependency::artifactId)
                .doesNotContain(
                        "letool-rule-engine-core", "letool-starter-rule-engine",
                        "letool-starter-job", "jasperreports");
        assertThat(dependencies)
                .filteredOn(dependency -> dependency.artifactId()
                        .equals("letool-starter-print-expression-spel"))
                .singleElement()
                .extracting(Dependency::optional)
                .isEqualTo("true");
    }

    /** 底层打印模块不能反向依赖 Spring Boot 主 Starter。 */
    @Test
    void shouldKeepLowerPrintModulesIndependentFromStarter() throws Exception {
        Path root = Path.of(System.getProperty("basedir")).getParent();
        List<String> lowerModules = List.of(
                "letool-starter-print",
                "letool-starter-print-template",
                "letool-starter-print-xml",
                "letool-starter-print-pdf",
                "letool-starter-print-expression-spel");

        for (String module : lowerModules) {
            assertThat(dependencies(root.resolve(module).resolve("pom.xml")))
                    .extracting(Dependency::artifactId)
                    .as("%s 不应反向依赖主 Starter", module)
                    .doesNotContain("letool-starter-print-spring-boot");
        }
    }

    /** 使用关闭外部实体的 JAXP 读取 Maven 直接依赖。 */
    private List<Dependency> dependencies(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Element project;
        try (var input = Files.newInputStream(pom)) {
            project = factory.newDocumentBuilder().parse(input).getDocumentElement();
        }

        List<Dependency> result = new ArrayList<>();
        NodeList dependencyNodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < dependencyNodes.getLength(); index++) {
            Element dependency = (Element) dependencyNodes.item(index);
            result.add(new Dependency(
                    childText(dependency, "artifactId"),
                    childText(dependency, "optional")));
        }
        return result;
    }

    /** 读取依赖节点的一层直接子元素，缺失时返回空串。 */
    private String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return "";
    }

    /** 测试内部使用的 Maven 依赖快照。 */
    private static final class Dependency {

        /** Maven artifactId。 */
        private final String artifactId;

        /** optional 原始声明。 */
        private final String optional;

        /** 保存解析出的最小依赖信息。 */
        private Dependency(String artifactId, String optional) {
            this.artifactId = artifactId;
            this.optional = optional;
        }

        /** @return Maven artifactId */
        private String artifactId() {
            return artifactId;
        }

        /** @return optional 原始声明 */
        private String optional() {
            return optional;
        }
    }
}
