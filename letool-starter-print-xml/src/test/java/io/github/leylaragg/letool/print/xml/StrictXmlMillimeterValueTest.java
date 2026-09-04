package io.github.leylaragg.letool.print.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 严格 XML 毫米值的语法和精确换算测试。
 *
 * @author leyland
 */
class StrictXmlMillimeterValueTest {

    /** 验证非负语法拒绝符号、前导零和超出精度的形式。 */
    @Test
    void shouldValidateUnsignedMillimeters() {
        assertThat(StrictXmlMillimeterValue.isUnsigned(null)).isFalse();
        assertThat(StrictXmlMillimeterValue.isUnsigned("0mm")).isTrue();
        assertThat(StrictXmlMillimeterValue.isUnsigned("9999.999mm")).isTrue();
        assertThat(StrictXmlMillimeterValue.isUnsigned("+1mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isUnsigned("01mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isUnsigned("1.0000mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isUnsigned("1e1mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isUnsigned("10000mm")).isFalse();
    }

    /** 验证带方向语法仅额外接受可选正负号。 */
    @Test
    void shouldValidateSignedMillimeters() {
        assertThat(StrictXmlMillimeterValue.isSigned(null)).isFalse();
        assertThat(StrictXmlMillimeterValue.isSigned("-9999.999mm")).isTrue();
        assertThat(StrictXmlMillimeterValue.isSigned("+1.25mm")).isTrue();
        assertThat(StrictXmlMillimeterValue.isSigned("01mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isSigned("-01mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isSigned("1.0000mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isSigned("1e1mm")).isFalse();
        assertThat(StrictXmlMillimeterValue.isSigned("10000mm")).isFalse();
    }

    /** 验证毫米单位大小写不影响严格语法识别。 */
    @Test
    void shouldIgnoreMillimeterUnitCase() {
        assertThat(StrictXmlMillimeterValue.isUnsigned("1.25MM")).isTrue();
        assertThat(StrictXmlMillimeterValue.isSigned("-1.25Mm")).isTrue();
    }

    /** 验证合法毫米值可以无损换算为整数微米。 */
    @Test
    void shouldConvertMillimetersExactly() {
        assertThat(StrictXmlMillimeterValue.toMicrometers("1.234mm")).isEqualTo(1_234);
        assertThat(StrictXmlMillimeterValue.toMicrometers("-0.001MM")).isEqualTo(-1);
        assertThat(StrictXmlMillimeterValue.toMicrometers("+9999.999mm")).isEqualTo(9_999_999);
    }
}
