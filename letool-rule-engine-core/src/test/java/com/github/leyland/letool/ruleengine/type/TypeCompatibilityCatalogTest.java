package com.github.leyland.letool.ruleengine.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeCompatibilityCatalogTest {

    @Test
    @DisplayName("类型目录版本、规则清单和指纹应由稳定规范共同锁定")
    void shouldExposeVersionedCanonicalCatalog() {
        assertThat(TypeCompatibility.CATALOG_VERSION).isEqualTo("1");
        assertThat(TypeCompatibility.CATALOG_RULES).containsExactly(
                "NUMERIC_PROMOTION:INTEGER_DECIMAL_TO_DECIMAL",
                "EQUALITY:EXACT_KIND_OR_NUMERIC",
                "ORDERING:NUMERIC_OR_EXACT_STRING_TEMPORAL",
                "TEMPORAL:NO_CROSS_KIND_CONVERSION",
                "NULL:EQUALITY_WITH_ANY_VALUE_OR_IS_NULL",
                "LOGICAL:BOOLEAN_ONLY",
                "IN:ELEMENT_EQUALITY_COMPATIBLE",
                "BETWEEN:SAME_ORDERING_DOMAIN",
                "DIVISION:DECIMAL128",
                "REMAINDER:BIG_DECIMAL_REMAINDER");
        assertThat(TypeCompatibility.TYPE_CATALOG_FINGERPRINT)
                .isEqualTo("b4de134aea98dcac114ed8e0bec450327e083dbc8fb0fae7918ca63a7af65ac9");
    }
}
