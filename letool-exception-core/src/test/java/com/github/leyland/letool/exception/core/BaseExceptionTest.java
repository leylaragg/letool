package com.github.leyland.letool.exception.core;

import com.github.leyland.letool.exception.code.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BaseExceptionTest {

    private static final ErrorCode CODE = ErrorCode.of("TEST_001", "资源 {0} 不存在");

    @Test
    void businessExceptionExposesStableFallbackAndDiagnosticMessage() {
        BusinessException exception = BusinessException.of(CODE, "patient-1");

        assertThat(exception.getErrorCode()).isSameAs(CODE);
        assertThat(exception.getCode()).isEqualTo("TEST_001");
        assertThat(exception.getFallbackMessage()).isEqualTo("资源 patient-1 不存在");
        assertThat(exception.getMessage()).isEqualTo("[TEST_001] 资源 patient-1 不存在");
        assertThat(exception.toString())
                .isEqualTo(BusinessException.class.getName() + ": [TEST_001] 资源 patient-1 不存在");
    }

    @Test
    void messageArgumentsAreDefensivelyCopiedOnInputAndOutput() {
        Object[] inputArguments = {"patient-1"};
        BusinessException exception = BusinessException.of(CODE, inputArguments);

        inputArguments[0] = "changed-input";
        Object[] outputArguments = exception.getMessageArgs();
        outputArguments[0] = "changed-output";

        assertThat(exception.getMessageArgs()).containsExactly("patient-1");
        assertThat(exception.getFallbackMessage()).isEqualTo("资源 patient-1 不存在");
    }

    @Test
    void customMessageBecomesTheNonLocalizableFallback() {
        BusinessException exception = BusinessException.custom(CODE, "患者规则正在执行");

        assertThat(exception.hasCustomMessage()).isTrue();
        assertThat(exception.getCustomMessage()).isEqualTo("患者规则正在执行");
        assertThat(exception.getFallbackMessage()).isEqualTo("患者规则正在执行");
        assertThat(exception.getMessage()).isEqualTo("[TEST_001] 患者规则正在执行");
    }

    @Test
    void systemExceptionRetainsCauseAndFormatsArguments() {
        IllegalStateException cause = new IllegalStateException("backend failed");

        SystemException exception = SystemException.causedBy(CODE, cause, "patient-1");

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessageArgs()).containsExactly("patient-1");
        assertThat(exception.getFallbackMessage()).isEqualTo("资源 patient-1 不存在");
    }

    @Test
    void malformedMessagePatternDoesNotPreventExceptionConstruction() {
        ErrorCode malformedCode = ErrorCode.of("BROKEN_001", "资源 {0 不存在");

        BusinessException exception = BusinessException.of(malformedCode, "patient-1");

        assertThat(exception.getMessage())
                .contains("[BROKEN_001]")
                .contains("资源 {0 不存在")
                .contains("patient-1");
    }

    @Test
    void businessExceptionSurvivesJavaSerializationRoundTrip() throws Exception {
        BusinessException original = BusinessException.of(CODE, "patient-1");

        BusinessException restored = roundTrip(original);

        assertThat(restored.getClass()).isEqualTo(BusinessException.class);
        assertThat(restored.getCode()).isEqualTo("TEST_001");
        assertThat(restored.getMessageArgs()).containsExactly("patient-1");
        assertThat(restored.getFallbackMessage()).isEqualTo("资源 patient-1 不存在");
        assertThat(restored.getMessage()).isEqualTo("[TEST_001] 资源 patient-1 不存在");
    }

    @Test
    void businessCustomFactoryRejectsNullMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BusinessException.custom(CODE, null))
                .withMessageContaining("customMessage");
    }

    @Test
    void systemCustomFactoryRejectsBlankMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SystemException.custom(CODE, " "))
                .withMessageContaining("customMessage");
    }

    @Test
    void businessCausedByFactoryRejectsNullCause() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BusinessException.causedBy(CODE, null, "patient-1"))
                .withMessageContaining("cause");
    }

    @Test
    void systemCausedByFactoryRejectsNullCause() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SystemException.causedBy(CODE, null, "patient-1"))
                .withMessageContaining("cause");
    }

    private static BusinessException roundTrip(BusinessException original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BusinessException) input.readObject();
        }
    }
}
