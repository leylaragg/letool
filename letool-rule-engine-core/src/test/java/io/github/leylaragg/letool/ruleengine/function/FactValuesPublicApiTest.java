package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactKind;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.ScalarFactValue;
import io.github.leylaragg.letool.ruleengine.fact.ObjectFactValue;
import io.github.leylaragg.letool.ruleengine.fact.ArrayFactValue;
import io.github.leylaragg.letool.ruleengine.fact.NullFactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 公共事实值工厂跨包可用性测试。 */
class FactValuesPublicApiTest {
    @Test
    void shouldCreateEveryPublicFactValueType() {
        assertThat(FactValues.nullValue().kind()).isEqualTo(FactKind.NULL);
        assertThat(FactValues.string("A").toSafeJavaValue()).isEqualTo("A");
        assertThat(FactValues.string('B').toSafeJavaValue()).isEqualTo("B");
        assertThat(FactValues.booleanValue(true).kind()).isEqualTo(FactKind.BOOLEAN);
        assertThat(FactValues.integer(3L).asBigInteger()).isEqualTo(BigInteger.valueOf(3));
        assertThat(FactValues.integer(BigInteger.TEN).kind()).isEqualTo(FactKind.INTEGER);
        assertThat(FactValues.decimal(new BigDecimal("1.5")).kind()).isEqualTo(FactKind.DECIMAL);
        assertThat(FactValues.date(LocalDate.of(2026, 1, 1)).kind()).isEqualTo(FactKind.DATE);
        assertThat(FactValues.dateTime(LocalDateTime.of(2026, 1, 1, 1, 1)).kind()).isEqualTo(FactKind.DATE_TIME);
        assertThat(FactValues.instant(Instant.EPOCH).kind()).isEqualTo(FactKind.INSTANT);
        assertThat(FactValues.fromJavaValue(null)).isSameAs(FactValues.nullValue());
        assertThat(FactValues.fromJavaValue(1, EngineLimits.defaults()).kind()).isEqualTo(FactKind.INTEGER);
    }

    @Test
    void shouldRejectNullInNonNullFactories() {
        for (Runnable call : new Runnable[]{
                () -> FactValues.string((String) null), () -> FactValues.integer((BigInteger) null),
                () -> FactValues.decimal(null), () -> FactValues.date(null),
                () -> FactValues.dateTime(null), () -> FactValues.instant(null)}) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(RuleEngineException.class,
                    exception -> assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
        }
    }

    @Test
    void shouldSealFactValueImplementations() {
        assertThat(FactValue.class.isSealed()).isTrue();
        assertThat(FactValue.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                ScalarFactValue.class, ObjectFactValue.class,
                ArrayFactValue.class, NullFactValue.class);
    }
}
