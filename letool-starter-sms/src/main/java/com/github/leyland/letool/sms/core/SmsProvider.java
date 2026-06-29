package com.github.leyland.letool.sms.core;

import com.github.leyland.letool.sms.model.SmsResult;

import java.util.List;
import java.util.Map;

// ======================== 类级别说明 ========================

/**
 * <p>短信服务提供者接口 — 定义统一的短信发送能力契约。</p>
 *
 * <h3>设计理念</h3>
 * <p>本接口采用<strong>策略模式</strong>，为不同短信服务商（阿里云、腾讯云等）
 * 提供统一的编程接口，使上层调用方无需关心底层具体的服务商实现。</p>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>单条发送</b>：向单个手机号发送指定模板的短信。</li>
 *   <li><b>批量发送</b>：向多个手机号批量发送相同内容的短信。</li>
 *   <li><b>提供商标识</b>：返回当前实现的提供商标识名称。</li>
 * </ul>
 *
 * <h3>扩展指南</h3>
 * <p>如需接入新的短信服务商（例如华为云、京东云等），只需：</p>
 * <ol>
 *   <li>实现本接口的全部方法。</li>
 *   <li>在 {@link com.github.leyland.letool.sms.config.SmsAutoConfiguration}
 *       中增加对应的 Bean 注册逻辑。</li>
 *   <li>在 {@link com.github.leyland.letool.sms.config.SmsProperties}
 *       中添加对应的配置内部类。</li>
 * </ol>
 *
 * <h3>实现说明</h3>
 * <ul>
 *   <li>当前提供三个内置实现：{@code AliyunSmsProvider}、
 *       {@code TencentSmsProvider}、{@code MockSmsProvider}。</li>
 *   <li>Mock 实现用于单元测试和开发环境，不产生实际费用。</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface SmsProvider {

    // ======================== 短信发送方法 ========================

    /**
     * 发送单条短信。
     *
     * <p>向单个手机号发送指定模板的短信，模板变量通过参数映射传入。</p>
     *
     * @param phone        目标手机号
     * @param templateCode 短信模板编码
     * @param params       模板变量映射（key 为变量名，value 为变量值）
     * @return 短信发送结果，包含请求 ID 及成功/失败状态
     * @throws com.github.leyland.letool.sms.exception.SmsException 发送过程出现异常时抛出
     */
    SmsResult send(String phone, String templateCode, Map<String, String> params);

    /**
     * 批量发送短信。
     *
     * <p>向多个手机号批量发送相同模板和参数的短信。
     * 实现类应根据服务商 API 的特性选择最优的批量发送策略
     * （例如使用批量 API 或循环调用单条发送 API）。</p>
     *
     * @param phones       目标手机号列表
     * @param templateCode 短信模板编码
     * @param params       模板变量映射（key 为变量名，value 为变量值）
     * @return 短信发送结果，表示批量发送的整体结果
     * @throws com.github.leyland.letool.sms.exception.SmsException 发送过程出现异常时抛出
     */
    SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params);

    /**
     * 获取当前短信服务提供者的名称标识。
     *
     * <p>名称应与 {@link com.github.leyland.letool.sms.config.SmsProperties}
     * 中 {@code defaultProvider} 配置项的值相对应，
     * 例如 {@code "aliyun"}、{@code "tencent"} 或 {@code "mock"}。</p>
     *
     * @return 提供者名称标识
     */
    String getProviderName();
}
