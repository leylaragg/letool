package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.reflection.ReflectionErrorCode;
import io.github.leylaragg.letool.tool.reflection.ReflectionOperationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BeanUtil} 的属性拷贝、目标创建、列表形状和失败契约测试。
 */
class BeanUtilTest {

    /** Bean 拷贝测试源类型。 */
    static final class Source {

        private String name;
        private int age;

        /**
         * 获取姓名。
         *
         * @return 姓名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置姓名。
         *
         * @param name 姓名
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取年龄。
         *
         * @return 年龄
         */
        public int getAge() {
            return age;
        }

        /**
         * 设置年龄。
         *
         * @param age 年龄
         */
        public void setAge(int age) {
            this.age = age;
        }
    }

    /** Bean 拷贝测试目标类型。 */
    static final class Target {

        private String name;
        private int age;

        /**
         * 获取姓名。
         *
         * @return 姓名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置姓名。
         *
         * @param name 姓名
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取年龄。
         *
         * @return 年龄
         */
        public int getAge() {
            return age;
        }

        /**
         * 设置年龄。
         *
         * @param age 年龄
         */
        public void setAge(int age) {
            this.age = age;
        }
    }

    /** 仅提供有参构造器的目标类型。 */
    static final class NoDefaultConstructor {

        /**
         * 创建不可由通用工具直接实例化的对象。
         *
         * @param value 测试值
         */
        private NoDefaultConstructor(String value) {
        }
    }

    /**
     * 验证标准拷贝、忽略属性和自定义目标工厂使用同一套属性复制语义。
     */
    @Test
    void shouldCopyWithIgnoredPropertiesAndTargetSupplier() {
        Source source = source("test", 18);

        Target copied = BeanUtil.copy(source, Target.class);
        Target ignored = BeanUtil.copy(source, Target.class, "age");
        Target supplied = BeanUtil.copy(source, Target::new);

        assertEquals("test", copied.getName());
        assertEquals(18, copied.getAge());
        assertEquals("test", ignored.getName());
        assertEquals(0, ignored.getAge());
        assertEquals("test", supplied.getName());
        assertEquals(18, supplied.getAge());
    }

    /**
     * 验证批量拷贝保留输入顺序和空元素，并始终返回可修改列表。
     */
    @Test
    void shouldPreserveListShapeAndReturnMutableEmptyList() {
        Source source = source("first", 1);

        List<Target> targets = BeanUtil.copyList(Arrays.asList(source, null), Target.class);
        List<Target> empty = BeanUtil.copyList(null, Target.class);
        empty.add(new Target());

        assertEquals(2, targets.size());
        assertEquals("first", targets.get(0).getName());
        assertNull(targets.get(1));
        assertEquals(1, empty.size());
    }

    /**
     * 验证目标类型属于必填控制参数，即使源对象为空也必须快速失败。
     */
    @Test
    void shouldRejectMissingTargetType() {
        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> BeanUtil.copy(null, (Class<Target>) null)
        );

        assertEquals(ReflectionErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }

    /**
     * 验证目标类型无法实例化时抛出稳定错误码并保留底层原因。
     */
    @Test
    void shouldReportInstantiationFailure() {
        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> BeanUtil.newInstance(NoDefaultConstructor.class)
        );

        assertEquals(ReflectionErrorCode.INSTANTIATION_FAILED.getCode(), exception.getCode());
        assertNotNull(exception.getCause());
    }

    /**
     * 创建测试源对象。
     *
     * @param name 姓名
     * @param age 年龄
     * @return 完成赋值的源对象
     */
    private static Source source(String name, int age) {
        Source source = new Source();
        source.setName(name);
        source.setAge(age);
        return source;
    }
}
