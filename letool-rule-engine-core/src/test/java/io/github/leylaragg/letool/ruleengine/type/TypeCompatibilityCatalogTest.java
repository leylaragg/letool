package io.github.leylaragg.letool.ruleengine.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeCompatibilityCatalogTest {

    @Test
    @DisplayName("类型目录版本、规则清单和摘要应由稳定规范共同锁定")
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
        assertThat(TypeCompatibility.TYPE_CATALOG_DIGEST)
                .isEqualTo("4483757a0cd93d90fb75b47eb2b310c2b7c10e62bb2ea3c73ff9c2677a519be5");
    }
}
