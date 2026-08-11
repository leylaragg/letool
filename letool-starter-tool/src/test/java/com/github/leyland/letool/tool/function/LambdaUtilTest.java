package com.github.leyland.letool.tool.function;

import com.github.leyland.letool.tool.reflection.ReflectionErrorCode;
import com.github.leyland.letool.tool.reflection.ReflectionOperationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LambdaUtil} 的 JavaBean Getter、record 访问器和非法方法引用契约测试。
 */
class LambdaUtilTest {

    /** 提供不同 JavaBeans Getter 形式的测试类型。 */
    static final class User {

        /**
         * 获取姓名。
         *
         * @return 姓名
         */
        String getName() {
            return "name";
        }

        /**
         * 获取启用状态。
         *
         * @return 是否启用
         */
        boolean isActive() {
            return true;
        }

        /**
         * 获取保留缩写形式的地址。
         *
         * @return 保留缩写形式的地址
         */
        String getURL() {
            return "url";
        }

        /**
         * 模拟不应被属性解析接受的静态转换方法。
         *
         * @param user 用户对象
         * @return 用户姓名
         */
        static String staticDisplayName(User user) {
            return user.getName();
        }
    }

    /** 提供标准 record 组件访问器的测试类型。 */
    record UserRecord(String name) {
    }

    /** 提供错误布尔 Getter 返回类型的测试类型。 */
    static final class InvalidBooleanGetter {

        /**
         * 返回不符合 JavaBeans 布尔 Getter 契约的状态。
         *
         * @return 非布尔就绪状态
         */
        String isReady() {
            return "ready";
        }
    }

    /**
     * 验证标准 Getter、布尔 Getter、缩写属性和 record 组件名称解析。
     */
    @Test
    void shouldResolveSupportedPropertyAccessors() {
        assertEquals("name", LambdaUtil.getPropertyName(User::getName));
        assertEquals("active", LambdaUtil.getPropertyName(User::isActive));
        assertEquals("URL", LambdaUtil.getPropertyName(User::getURL));
        assertEquals("name", LambdaUtil.getPropertyName(UserRecord::name));
    }

    /**
     * 验证普通 Lambda 和静态方法不会被误识别为 Bean 属性。
     */
    @Test
    void shouldRejectOrdinaryLambdaAndStaticMethod() {
        ReflectionOperationException ordinary = assertThrows(
                ReflectionOperationException.class,
                () -> LambdaUtil.getPropertyName(
                        (LambdaUtil.SFunction<User, String>) user -> user.getName()
                )
        );
        ReflectionOperationException staticMethod = assertThrows(
                ReflectionOperationException.class,
                () -> LambdaUtil.getPropertyName(User::staticDisplayName)
        );

        assertEquals(ReflectionErrorCode.LAMBDA_RESOLUTION_FAILED.getCode(), ordinary.getCode());
        assertEquals(ReflectionErrorCode.LAMBDA_RESOLUTION_FAILED.getCode(), staticMethod.getCode());
    }

    /**
     * 验证 {@code isXxx} 只有返回 primitive boolean 时才属于 JavaBeans Getter。
     */
    @Test
    void shouldRejectInvalidBooleanGetter() {
        ReflectionOperationException exception = assertThrows(
                ReflectionOperationException.class,
                () -> LambdaUtil.getPropertyName(InvalidBooleanGetter::isReady)
        );

        assertEquals(ReflectionErrorCode.LAMBDA_RESOLUTION_FAILED.getCode(), exception.getCode());
    }
}
