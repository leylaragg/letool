package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.enums.CodeEnum;
import com.github.leyland.letool.tool.enums.DescribedEnum;
import com.github.leyland.letool.tool.value.ValueErrorCode;
import com.github.leyland.letool.tool.value.ValueOperationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 枚举工具契约适配、兼容查找和失败语义测试。
 */
class EnumUtilTest {

    /**
     * 验证业务枚举契约无需反射即可完成查找和有序映射。
     */
    @Test
    void shouldUseBusinessEnumContracts() {
        assertEquals(Optional.of(ContractStatus.ACTIVE), EnumUtil.findByCode(ContractStatus.class, 1));
        assertEquals(ContractStatus.INACTIVE, EnumUtil.getByName(ContractStatus.class, "INACTIVE"));

        Map<String, Object> options = EnumUtil.toMap(ContractStatus.class);
        assertEquals(List.of("激活", "禁用"), new ArrayList<>(options.keySet()));
        assertEquals(List.of(1, 0), new ArrayList<>(options.values()));
    }

    /**
     * 验证旧枚举仍可通过 Getter 或私有字段查找，未命中时保持兼容返回空值。
     */
    @Test
    void shouldSupportLegacyGetterAndFieldLookup() {
        assertEquals(LegacyStatus.OPEN, EnumUtil.getByCode(LegacyStatus.class, "O"));
        assertEquals(
                Optional.of(LegacyStatus.CLOSED),
                EnumUtil.findBy(LegacyStatus.class, "alias", "closed")
        );
        assertNull(EnumUtil.getByCode(LegacyStatus.class, "UNKNOWN"));
    }

    /**
     * 验证严格查询未命中时提供稳定错误码，而不是要求业务层重复判空。
     */
    @Test
    void shouldFailStrictLookupWithStableError() {
        ValueOperationException exception = assertThrows(
                ValueOperationException.class,
                () -> EnumUtil.requireByCode(ContractStatus.class, 99)
        );

        assertEquals(ValueErrorCode.ENUM_CONSTANT_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 验证属性访问失败不会被静默吞掉。
     */
    @Test
    void shouldExposePropertyAccessFailure() {
        ValueOperationException exception = assertThrows(
                ValueOperationException.class,
                () -> EnumUtil.getBy(LegacyStatus.class, "missing", "value")
        );

        assertEquals(ValueErrorCode.ENUM_ACCESS_FAILED, exception.getErrorCode());
    }

    /**
     * 验证重复描述不会静默覆盖前一个业务选项。
     */
    @Test
    void shouldRejectDuplicateDescription() {
        ValueOperationException exception = assertThrows(
                ValueOperationException.class,
                () -> EnumUtil.toMap(DuplicateStatus.class)
        );

        assertEquals(ValueErrorCode.DUPLICATE_ENUM_LABEL, exception.getErrorCode());
    }

    /**
     * 实现标准编码和描述契约的业务状态。
     */
    private enum ContractStatus implements CodeEnum<Integer>, DescribedEnum {
        /** 启用状态。 */
        ACTIVE(1, "激活"),
        /** 禁用状态。 */
        INACTIVE(0, "禁用");

        /** 业务编码。 */
        private final Integer code;
        /** 展示描述。 */
        private final String description;

        /**
         * 创建契约枚举项。
         *
         * @param code 业务编码
         * @param description 展示描述
         */
        ContractStatus(Integer code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 获取业务编码。
         *
         * @return 业务编码
         */
        @Override
        public Integer getCode() {
            return code;
        }

        /**
         * 获取展示描述。
         *
         * @return 展示描述
         */
        @Override
        public String getDescription() {
            return description;
        }
    }

    /**
     * 未实现新契约的历史枚举。
     */
    private enum LegacyStatus {
        /** 开启状态。 */
        OPEN("O", "open"),
        /** 关闭状态。 */
        CLOSED("C", "closed");

        /** 历史编码字段。 */
        private final String code;
        /** 仅通过私有字段暴露的历史别名。 */
        private final String alias;

        /**
         * 创建历史枚举项。
         *
         * @param code 历史编码
         * @param alias 历史别名
         */
        LegacyStatus(String code, String alias) {
            this.code = code;
            this.alias = alias;
        }

        /**
         * 获取历史编码。
         *
         * @return 历史编码
         */
        public String getCode() {
            return code;
        }
    }

    /**
     * 描述重复的错误业务枚举。
     */
    private enum DuplicateStatus implements CodeEnum<Integer>, DescribedEnum {
        /** 第一个选项。 */
        FIRST(1),
        /** 第二个选项。 */
        SECOND(2);

        /** 业务编码。 */
        private final Integer code;

        /**
         * 创建重复描述枚举项。
         *
         * @param code 业务编码
         */
        DuplicateStatus(Integer code) {
            this.code = code;
        }

        /**
         * 获取业务编码。
         *
         * @return 业务编码
         */
        @Override
        public Integer getCode() {
            return code;
        }

        /**
         * 获取重复描述。
         *
         * @return 固定描述
         */
        @Override
        public String getDescription() {
            return "重复";
        }
    }
}
