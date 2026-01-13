package mapper;

import com.github.leyland.letool.data.desensitize.DesensitizeHandlerHolder;
import com.github.leyland.letool.data.desensitize.DesensitizeUtil;
import com.github.leyland.letool.data.desensitize.handler.DesensitizeHandler;
import com.github.leyland.letool.data.desensitize.handler.IndexDesensitizeHandler;
import com.github.leyland.letool.data.desensitize.handler.RegexDesensitizeHandler;
import com.github.leyland.letool.data.desensitize.handler.SlideDesensitizeHandler;
import com.github.leyland.letool.data.desensitize.rule.*;

/**
 * 优化后的脱敏功能演示
 * 展示策略模式、规则系统、SPI 扩展等新特性
 *
 * @author leyland
 * @date 2025-01-08
 */
public class OptimizedDesensitizeDemo {

    public static void main(String[] args) {
        // ========== 基础脱敏演示 ==========
        basicDesensitizeDemo();

        // ========== 策略模式演示 ==========
        strategyPatternDemo();

        // ========== 规则系统演示 ==========
        ruleSystemDemo();

        // ========== 高级功能演示 ==========
        advancedFeaturesDemo();

        // ========== 自定义处理器演示 ==========
        customHandlerDemo();
    }

    /**
     * 基础脱敏演示
     */
    private static void basicDesensitizeDemo() {
        System.out.println("========== 基础脱敏演示 ==========");

        System.out.println("中文姓名: " + DesensitizeUtil.maskChineseName("张小明"));
        System.out.println("身份证号: " + DesensitizeUtil.maskIdCard("430123199001011234"));
        System.out.println("手机号码: " + DesensitizeUtil.maskPhone("13800138000"));
        System.out.println("银行卡号: " + DesensitizeUtil.maskBankCard("6222021234567890123"));
        System.out.println("地址信息: " + DesensitizeUtil.maskAddress("北京市朝阳区xxx街道xxx号"));
        System.out.println("邮箱地址: " + DesensitizeUtil.maskEmail("zhangsan@example.com"));
        System.out.println("密码信息: " + DesensitizeUtil.maskPassword("MyPassword123"));
        System.out.println();
    }

    /**
     * 策略模式演示
     */
    private static void strategyPatternDemo() {
        System.out.println("========== 策略模式演示 ==========");

        // 获取滑块脱敏处理器
        SlideDesensitizeHandler slideHandler = DesensitizeHandlerHolder.getSlideHandler();
        System.out.println("滑块脱敏(2,4): " + slideHandler.mask("13800138000", 2, 4));
        System.out.println("滑块脱敏(3,2): " + slideHandler.mask("13800138000", 3, 2));

        // 获取正则脱敏处理器
        RegexDesensitizeHandler regexHandler = DesensitizeHandlerHolder.getRegexHandler();
        System.out.println("正则脱敏: " + regexHandler.mask("test.demo@qq.com", "(^.)[^@]*(@.*)$", "$1****$2"));

        // 获取索引脱敏处理器
        IndexDesensitizeHandler indexHandler = DesensitizeHandlerHolder.getIndexHandler();
        System.out.println("索引脱敏(1,3-5,9-): " + indexHandler.mask("1234567890123456789", "1", "3-5", "9-"));
        System.out.println();
    }

    /**
     * 规则系统演示
     */
    private static void ruleSystemDemo() {
        System.out.println("========== 规则系统演示 ==========");

        SlideDesensitizeHandler handler = DesensitizeHandlerHolder.getSlideHandler();

        // 使用预定义规则
        System.out.println("手机号规则: " + handler.mask("13800138000", new PhoneNumberSlideRule()));
        System.out.println("身份证规则: " + handler.mask("430123199001011234", new IdCardSlideRule()));
        System.out.println("银行卡规则: " + handler.mask("6222021234567890123", new BankCardSlideRule()));
        System.out.println("地址规则: " + handler.mask("北京市朝阳区xxx街道xxx号", new AddressSlideRule()));

        // 使用邮箱正则规则
        RegexDesensitizeHandler regexHandler = DesensitizeHandlerHolder.getRegexHandler();
        System.out.println("邮箱正则规则: " + regexHandler.mask("zhangsan@example.com", new EmailRegexRule()));
        System.out.println();
    }

    /**
     * 高级功能演示
     */
    private static void advancedFeaturesDemo() {
        System.out.println("========== 高级功能演示 ==========");

        // 自定义滑块脱敏
        System.out.println("自定义滑块(4,6,###): " +
            DesensitizeUtil.maskBySlide("12345678901234567890", 4, 6, "###", false));

        // 反转脱敏
        System.out.println("反转脱敏: " +
            DesensitizeUtil.maskBySlide("HelloWorld", 2, 3, "*", true));

        // 复杂索引规则
        System.out.println("复杂索引(0,2-4,6,8-): " +
            DesensitizeUtil.maskByIndex("12345678901234567890", '0', false, "0", "2-4", "6", "8-"));

        // 自定义正则
        System.out.println("自定义正则: " +
            DesensitizeUtil.maskByRegex("张三-13800138000", "(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        System.out.println();
    }

    /**
     * 自定义处理器演示
     */
    private static void customHandlerDemo() {
        System.out.println("========== 自定义处理器演示 ==========");

        // 注册自定义处理器
        DesensitizeHandlerHolder.register(CustomDesensitizeHandler.class, new CustomDesensitizeHandler());

        // 使用自定义处理器
        CustomDesensitizeHandler customHandler = DesensitizeHandlerHolder.getHandler(CustomDesensitizeHandler.class);
        System.out.println("自定义处理器: " + customHandler.mask("Hello World"));
        System.out.println();
    }

    /**
     * 自定义脱敏处理器示例
     */
    static class CustomDesensitizeHandler implements DesensitizeHandler {
        @Override
        public String mask(String origin) {
            if (origin == null) {
                return null;
            }
            // 简单的自定义逻辑：每个单词首字母大写，其余小写
            String[] words = origin.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
                }
            }
            return sb.toString().trim();
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return String.class.isAssignableFrom(clazz);
        }
    }
}
