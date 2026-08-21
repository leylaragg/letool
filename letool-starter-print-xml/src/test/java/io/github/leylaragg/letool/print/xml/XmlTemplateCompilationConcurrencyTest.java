package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.api.OutputFormat;
import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.template.InMemoryTemplateRepository;
import io.github.leylaragg.letool.print.template.TemplateCompilationKey;
import io.github.leylaragg.letool.print.template.TemplateDefinition;
import io.github.leylaragg.letool.print.template.TemplateSet;
import io.github.leylaragg.letool.print.template.TemplateSetPublisher;
import io.github.leylaragg.letool.print.template.TemplateType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML 模板编译缓存的同键并发装载测试。
 *
 * @author leyland
 */
class XmlTemplateCompilationConcurrencyTest {

    /** 百个并发请求应共享两层缓存各自唯一的一次装载。 */
    @Test
    void shouldLoadBothCacheLayersOnceForConcurrentKey() throws Exception {
        TemplateSet set = templateSet();
        PrintTemplate template = set.require("main").template();
        TemplateCompilationKey key = new TemplateCompilationKey(
                set.version(), set.digest(), template.templateCode(),
                template.dslVersion(), template.contextVersion(), 1, OutputFormat.PDF);
        XmlTemplateCompilationCache cache = new XmlTemplateCompilationCache(new XmlTemplateSetCompiler());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        try {
            List<Future<ResolvedXmlTemplate>> futures = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.resolve(set, key);
                }));
            }
            start.countDown();

            ResolvedXmlTemplate expected = futures.get(0).get(10, TimeUnit.SECONDS);
            for (Future<ResolvedXmlTemplate> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(expected);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        XmlTemplateCompilationCacheStats stats = cache.stats();
        assertThat(stats.templateSetLoadSuccessCount()).isEqualTo(1);
        assertThat(stats.templateLoadSuccessCount()).isEqualTo(1);
    }

    /** 创建并发布并发测试使用的单文档集合。 */
    private TemplateSet templateSet() {
        String source = "<document xmlns=\"" + XmlDsl.NAMESPACE_V1
                + "\" context-version=\"1\"><page><page-body><paragraph>并发</paragraph></page-body></page></document>";
        PrintTemplate template = new PrintTemplate("main", TemplateFormat.LETOOL_XML, 1, 7, 1,
                source.getBytes(StandardCharsets.UTF_8));
        return new TemplateSetPublisher(new InMemoryTemplateRepository(), List.of())
                .publish(7, List.of(new TemplateDefinition(TemplateType.DOCUMENT, template)));
    }
}
