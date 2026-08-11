package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.value.ValueErrorCode;
import com.github.leyland.letool.tool.value.ValueOperationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 集合工具可变性、顺序和参数契约测试。
 */
class CollUtilTest {

    /**
     * 验证空输入仍返回独立的可变快照，调用方可以继续组装业务数据。
     */
    @Test
    void shouldReturnMutableIndependentSnapshotsForEmptyInputs() {
        List<String> extracted = CollUtil.extract(List.of(), String::trim);
        List<String> union = CollUtil.union(null, null);
        List<List<String>> partitions = CollUtil.partition(List.of(), 1);

        extracted.add("value");
        union.add("value");
        partitions.add(new ArrayList<>());

        assertEquals(List.of("value"), extracted);
        assertEquals(List.of("value"), union);
        assertEquals(1, partitions.size());
    }

    /**
     * 验证集合运算按第一个集合的出现顺序去重输出。
     */
    @Test
    void shouldPreserveEncounterOrderForSetOperations() {
        assertEquals(
                List.of(3, 2),
                CollUtil.intersection(List.of(3, 2, 3, 1), List.of(2, 3))
        );
        assertEquals(List.of(3, 2, 1, 4), CollUtil.union(List.of(3, 2, 3), List.of(1, 4)));
        assertEquals(List.of(3, 1), CollUtil.subtract(List.of(3, 2, 3, 1), List.of(2)));
    }

    /**
     * 验证转换为 Map 时保留顺序、首值和自定义值映射结果。
     */
    @Test
    void shouldBuildOrderedMapAndKeepFirstDuplicate() {
        List<String> source = List.of("b1", "a11", "b222");
        Map<Character, String> values = CollUtil.toMap(source, value -> value.charAt(0));
        Map<Character, Integer> lengths = CollUtil.toMap(
                source,
                value -> value.charAt(0),
                String::length
        );

        assertEquals(List.of('b', 'a'), new ArrayList<>(values.keySet()));
        assertEquals("b1", values.get('b'));
        assertEquals(Map.of('b', 2, 'a', 3), lengths);
    }

    /**
     * 验证空列表同样执行分片大小校验，避免非法参数被输入数据状态掩盖。
     */
    @Test
    void shouldValidatePartitionSizeBeforeEmptyShortcut() {
        ValueOperationException exception = assertThrows(
                ValueOperationException.class,
                () -> CollUtil.partition(List.of(), 0)
        );

        assertEquals(ValueErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
    }

    /**
     * 验证必填映射函数缺失时抛出统一异常，而不是泄漏空指针异常。
     */
    @Test
    void shouldRejectMissingMapperWithStableError() {
        ValueOperationException exception = assertThrows(
                ValueOperationException.class,
                () -> CollUtil.extract(List.of("value"), null)
        );

        assertEquals(ValueErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
    }
}
