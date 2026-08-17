package io.github.leylaragg.letool.print.template;

import io.github.leylaragg.letool.print.api.PrintTemplate;
import io.github.leylaragg.letool.print.api.TemplateFormat;
import io.github.leylaragg.letool.print.exception.PrintValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板集合发布编排测试。
 *
 * @author leyland
 */
class TemplateSetPublisherTest {

    /** 候选集合应先完成校验，再原子发布并激活。 */
    @Test
    void shouldValidateBeforePublishingAndActivating() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        AtomicReference<TemplateSet> validated = new AtomicReference<>();
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(validated::set));

        TemplateSet published = publisher.publishAndActivate(
                1, List.of(document("main", 1, "MAIN")));

        assertThat(validated.get()).isSameAs(published);
        assertThat(repository.current()).containsSame(published);
    }

    /** 普通发布只保存版本，不应改变当前指针。 */
    @Test
    void shouldPublishWithoutActivation() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(repository, List.of());

        TemplateSet published = publisher.publish(
                1, List.of(document("main", 1, "MAIN")));

        assertThat(repository.find(1)).containsSame(published);
        assertThat(repository.current()).isEmpty();
    }

    /** 校验器拒绝候选集合后，仓库不能留下半成品。 */
    @Test
    void shouldNotPublishWhenValidatorRejectsCandidate() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(candidate -> {
                    throw PrintValidationException.invalidRequest("校验拒绝");
                }));

        assertThatThrownBy(() -> publisher.publish(
                1, List.of(document("main", 1, "SECRET"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageContaining("校验拒绝");
        assertThat(repository.find(1)).isEmpty();
    }

    /** 未知校验异常不得向调用方暴露实现类型或业务值。 */
    @Test
    void shouldSanitizeUnexpectedValidatorFailure() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        TemplateSetPublisher publisher = new TemplateSetPublisher(
                repository, List.of(candidate -> {
                    throw new IllegalStateException("secret-business-value");
                }));

        assertThatThrownBy(() -> publisher.publish(
                1, List.of(document("main", 1, "SECRET"))))
                .isInstanceOf(PrintValidationException.class)
                .hasMessageNotContaining("secret-business-value")
                .hasMessageNotContaining("IllegalStateException")
                .hasNoCause();
        assertThat(repository.find(1)).isEmpty();
    }

    /** 发布器构造后不再受调用方列表修改影响。 */
    @Test
    void shouldFreezeValidatorCollection() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        AtomicInteger validations = new AtomicInteger();
        List<TemplateSetValidator> validators = new ArrayList<>();
        validators.add(candidate -> validations.incrementAndGet());
        TemplateSetPublisher publisher = new TemplateSetPublisher(repository, validators);
        validators.clear();

        publisher.publish(1, List.of(document("main", 1, "MAIN")));

        assertThat(validations.get()).isEqualTo(1);
    }

    /** 发布器的必要协作者和校验器元素都不能为空。 */
    @Test
    void shouldRejectNullCollaborators() {
        InMemoryTemplateRepository repository = new InMemoryTemplateRepository();
        List<TemplateSetValidator> validatorsWithNull = new ArrayList<>();
        validatorsWithNull.add(null);

        assertThatNullPointerException()
                .isThrownBy(() -> new TemplateSetPublisher(null, List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new TemplateSetPublisher(repository, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new TemplateSetPublisher(repository, validatorsWithNull));
    }

    /** 测试发布使用的文档定义。 */
    private TemplateDefinition document(String code, long version, String source) {
        PrintTemplate template = new PrintTemplate(
                code, TemplateFormat.LETOOL_XML, 1, version, 1,
                source.getBytes(StandardCharsets.UTF_8));
        return new TemplateDefinition(TemplateType.DOCUMENT, template);
    }
}
