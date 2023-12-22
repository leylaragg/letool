package com.github.leyland.letool.demo.spring.mvc.validator;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 该自定义约束注解用于判断奇偶性，可以标注在方法、字段、参数、类型 上面
 * <p>
 * 基于规范，当被校验对象为null时，校验为通过
 */
@Target({METHOD, FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = Odevity.MyConstraintValidator.class)
public @interface Odevity {

    /**
     * 在违反约束时返回创建错误消息的默认key
     */
    String message() default "{com.spring.mvc.config.Odevity.message}";

    /**
     * 允许此约束所属的规范验证组
     */
    Class<?>[] groups() default {};

    /**
     * 可以将自定义有效Payload对象分配给约束，通常未使用
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 设置校验的模式
     * ODD——奇数，EVEN——偶数
     */
    OdevityMode value();


    /**
     * Validator校验器的实现，真正的校验的逻辑
     */
    class MyConstraintValidator implements ConstraintValidator<Odevity, Long> {

        private OdevityMode odevityMode;

        /**
         * 初始化Validator校验器
         *
         * @param constraintAnnotation 当前Odevity注解实例
         */
        @Override
        public void initialize(Odevity constraintAnnotation) {
            odevityMode = constraintAnnotation.value();
        }

        /**
         * 执行校验的路基
         *
         * @param value   注解的数据的值
         * @param context 校验上下文
         * @return 是否校验通过，false不通过，true通过
         */
        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            boolean flag = value % 2 == 0;
            return flag && odevityMode == OdevityMode.EVEN ||
                    (!flag && odevityMode == OdevityMode.ODD);
        }
    }

    /**
     * 奇偶性的枚举常量
     */
    enum OdevityMode {
        /**
         * 奇数
         */
        ODD,

        /**
         * 偶数
         */
        EVEN;
    }

}
