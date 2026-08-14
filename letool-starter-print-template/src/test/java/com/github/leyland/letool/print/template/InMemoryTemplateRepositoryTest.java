package com.github.leyland.letool.print.template;

import com.github.leyland.letool.print.api.PrintTemplate;
import com.github.leyland.letool.print.api.TemplateFormat;
import com.github.leyland.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内存模板仓库的版本与并发测试。
 *
 * @author leyland
 */
class InMemoryTemplateRepositoryTest {

    /** 单纯发布新版本时不应隐式切换当前版本。 */
    @Test
    void shouldPublishWithoutChangingCurrentVersion() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSet versionOne = templateSet(1, "ONE");

        repository.publish(versionOne);

        assertThat(repository.find(1)).containsSame(versionOne);
        assertThat(repository.current()).isEmpty();
    }

    /** 发布并激活应保留所有已发布的旧版本。 */
    @Test
    void shouldPublishAndActivateAtomicallyWhileKeepingOldVersion() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSet versionOne = templateSet(1, "ONE");
        TemplateSet versionTwo = templateSet(2, "TWO");
        repository.publishAndActivate(versionOne);

        repository.publishAndActivate(versionTwo);

        assertThat(repository.current()).containsSame(versionTwo);
        assertThat(repository.find(1)).containsSame(versionOne);
        assertThat(repository.find(2)).containsSame(versionTwo);
    }

    /** 已发布版本不可覆盖，未发布版本也不能激活。 */
    @Test
    void shouldRejectDuplicateOrMissingVersion() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        repository.publish(templateSet(1, "ONE"));

        assertThatThrownBy(() -> repository.publish(templateSet(1, "OTHER")))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> repository.activate(2))
                .isInstanceOf(PrintValidationException.class);
        assertThatThrownBy(() -> repository.find(0))
                .isInstanceOf(PrintValidationException.class);
        assertThatNullPointerException().isThrownBy(() -> repository.publish(null));
    }

    /** 激活已发布版本时只切换指针，不复制或替换集合。 */
    @Test
    void shouldActivatePublishedVersion() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSet versionOne = templateSet(1, "ONE");
        TemplateSet versionTwo = templateSet(2, "TWO");
        repository.publishAndActivate(versionOne);
        repository.publish(versionTwo);

        TemplateSet activated = repository.activate(2);

        assertThat(activated).isSameAs(versionTwo);
        assertThat(repository.current()).containsSame(versionTwo);
    }

    /** 同一版本并发发布时只能有一个调用成功。 */
    @Test
    void shouldAllowOnlyOneConcurrentPublisherForSameVersion() throws Exception {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 16; index++) {
                String content = "CONTENT-" + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        repository.publish(templateSet(1, content));
                        successes.incrementAndGet();
                    } catch (PrintValidationException exception) {
                        failures.incrementAndGet();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            awaitAll(futures);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(15);
        assertThat(repository.find(1)).isPresent();
    }

    /** 读线程在连续切换期间只能看到完整集合快照。 */
    @Test
    void shouldExposeCompleteSnapshotsDuringConcurrentPublishing() throws Exception {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        repository.publishAndActivate(templateSet(1, "CONTENT-1"));
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> {
                await(start);
                for (int version = 2; version <= 60; version++) {
                    repository.publishAndActivate(
                            templateSet(version, "CONTENT-" + version));
                }
            }));
            for (int reader = 0; reader < 5; reader++) {
                futures.add(executor.submit(() -> {
                    await(start);
                    for (int iteration = 0; iteration < 2_000; iteration++) {
                        assertComplete(repository.current().orElseThrow());
                    }
                }));
            }
            start.countDown();
            awaitAll(futures);
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.current().orElseThrow().version()).isEqualTo(60);
    }

    /** 校验一次读取到的集合内部字段保持一致。 */
    private void assertComplete(TemplateSet snapshot) {
        assertThat(snapshot.templateCodes()).isNotEmpty();
        assertThat(snapshot.digest()).matches("[0-9a-f]{64}");
        assertThat(snapshot.documentCount() + snapshot.fragmentCount())
                .isEqualTo(snapshot.definitions().size());
        assertThat(snapshot.require("main").template().templateSetVersion())
                .isEqualTo(snapshot.version());
    }

    /** 等待并传播任务异常，避免并发断言被线程池吞掉。 */
    private void awaitAll(List<Future<?>> futures) throws Exception {
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
    }

    /** 在线程任务中等待统一起跑信号。 */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", exception);
        }
    }

    /** 创建仅包含一个文档的测试集合。 */
    private TemplateSet templateSet(long version, String content) {
        PrintTemplate template = new PrintTemplate(
                "main", TemplateFormat.LETOOL_XML, 1, version, 1,
                content.getBytes(StandardCharsets.UTF_8));
        return TemplateSetFactory.standard().create(
                version, List.of(new TemplateDefinition(TemplateType.DOCUMENT, template)));
    }
}
