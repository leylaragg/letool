package io.github.leylaragg.letool.exception.message;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import io.github.leylaragg.letool.exception.core.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMessageResolverTest {

    private static final ErrorCode FIELD_MISSING =
            ErrorCode.of("TEST_001", "字段 {0} 不存在");

    @AfterEach
    void clearLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolveUsesConfiguredDefaultWhenNoLocaleContextIsBound() {
        DefaultMessageResolver resolver =
                new DefaultMessageResolver(Locale.SIMPLIFIED_CHINESE);

        String message = resolver.resolve(BusinessException.of(FIELD_MISSING, "age"));

        assertThat(message).isEqualTo("字段 age 不存在");
    }

    @Test
    void resolveUsesBoundLocaleContextWhenPresent() {
        DefaultMessageResolver resolver = new DefaultMessageResolver(Locale.GERMANY);
        ErrorCode code = ErrorCode.of("TEST_002", "value {0,number}");
        LocaleContextHolder.setLocale(Locale.US);

        String message = resolver.resolve(BusinessException.of(code, 1234));

        assertThat(message).isEqualTo("value 1,234");
    }

    @Test
    void customExceptionMessageBypassesDefaultFormatting() {
        DefaultMessageResolver resolver =
                new DefaultMessageResolver(Locale.SIMPLIFIED_CHINESE);
        BusinessException exception =
                BusinessException.custom(FIELD_MISSING, "exact custom text");

        assertThat(resolver.resolve(exception)).isEqualTo("exact custom text");
    }

    @Test
    void resolveErrorCodeFormatsNumbersWithRequestedLocale() {
        DefaultMessageResolver resolver =
                new DefaultMessageResolver(Locale.SIMPLIFIED_CHINESE);
        ErrorCode code = ErrorCode.of("TEST_003", "value {0,number}");

        String message = resolver.resolve(code, Locale.US, 1234);

        assertThat(message).contains("1,234");
    }

    @Test
    void nullRequestedLocaleFallsBackToConfiguredDefault() {
        DefaultMessageResolver resolver = new DefaultMessageResolver(Locale.GERMANY);
        ErrorCode code = ErrorCode.of("TEST_004", "value {0,number}");

        String message = resolver.resolve(code, null, 1234);

        assertThat(message).isEqualTo("value 1.234");
    }

    @Test
    void constructorAndResolveMethodsRequireTheirPrimaryInputs() {
        DefaultMessageResolver resolver = new DefaultMessageResolver(Locale.ENGLISH);

        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultMessageResolver(null))
                .withMessageContaining("defaultLocale");
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null))
                .withMessageContaining("exception");
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null, Locale.ENGLISH))
                .withMessageContaining("exception");
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null, Locale.ENGLISH, "value"))
                .withMessageContaining("errorCode");
    }

    @Test
    void bundleContributorDefensivelyCopiesAndExposesUnmodifiableBasenames() {
        String[] input = {"i18n/application", "i18n/shared"};

        MessageBundleContributor contributor = MessageBundleContributor.of(input);
        input[0] = "changed";

        assertThat(contributor.getBasenames())
                .containsExactly("i18n/application", "i18n/shared");
        assertThatThrownBy(() -> contributor.getBasenames().add("i18n/other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bundleContributorRejectsMissingOrInvalidBasenames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBundleContributor.of((String[]) null))
                .withMessageContaining("basenames");
        assertThatIllegalArgumentException()
                .isThrownBy(MessageBundleContributor::of)
                .withMessageContaining("basenames");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBundleContributor.of("i18n/application", null))
                .withMessageContaining("basenames[1]");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessageBundleContributor.of(" "))
                .withMessageContaining("basenames[0]");
    }
}
