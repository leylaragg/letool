package com.github.leyland.letool.sms.provider;

import com.github.leyland.letool.sms.config.SmsProperties;
import com.github.leyland.letool.sms.core.SmsProvider;
import com.github.leyland.letool.sms.exception.SmsException;
import com.github.leyland.letool.sms.model.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// ======================== 类级别说明 ========================

/**
 * <p>阿里云短信服务提供者 — 基于阿里云 SMS SDK 的短信发送实现。</p>
 *
 * <h3>职责</h3>
 * <p>封装阿里云短信服务的 API 调用逻辑，将上层统一的 {@link SmsProvider} 接口
 * 转换为对阿里云 SMS API 的具体调用。</p>
 *
 * <h3>实现说明</h3>
 * <ul>
 *   <li>当前为<strong>桩实现（Stub）</strong>，即不依赖真实的阿里云 SMS SDK，
 *       而是模拟 API 调用的完整流程并记录日志。</li>
 *   <li>在生产环境中，替换为真实的 SDK 调用即可，接口保持完全兼容。</li>
 *   <li>单条和批量发送均构造唯一的 {@code requestId}，模拟 API 返回。</li>
 * </ul>
 *
 * <h3>日志策略</h3>
 * <p>所有发送的短信内容均在 <b>INFO</b> 级别记录，便于开发环境追踪和审计。</p>
 *
 * <h3>配置依赖</h3>
 * <p>需要配置 {@code letool.sms.aliyun} 下的 {@code accessKeyId}、{@code accessKeySecret}、
 * {@code signName} 和 {@code regionId} 四项参数。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AliyunSmsProvider implements SmsProvider {

    // ======================== 常量与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    /** 提供商标识 */
    public static final String PROVIDER_NAME = "aliyun";

    /** 阿里云短信配置 */
    private final SmsProperties.Aliyun config;

    // ======================== 构造方法 ========================

    /**
     * 使用阿里云短信配置构造提供者实例。
     *
     * @param config 阿里云短信配置属性
     */
    public AliyunSmsProvider(SmsProperties.Aliyun config) {
        this.config = config;
        log.warn("AliyunSmsProvider initialized in STUB mode - no real Aliyun SMS API calls will be made.");
    }

    // ======================== 短信发送实现 ========================

    /**
     * 发送单条短信。
     *
     * <p>模拟阿里云 SMS API 调用流程：参数校验、构造请求、调用 API、解析响应。</p>
     *
     * @param phone        目标手机号
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 短信发送结果
     * @throws SmsException 参数校验失败或发送异常时抛出
     */
    @Override
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        // ---- 参数校验 ----
        if (phone == null || phone.isBlank()) {
            throw new SmsException("手机号不能为空");
        }
        if (templateCode == null || templateCode.isBlank()) {
            throw new SmsException("短信模板编码不能为空");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");

        try {
            // ---- 日志记录 ----
            log.info("[阿里云短信] 发送单条短信 | phone={} | templateCode={} | signName={} | regionId={} | params={} | requestId={}",
                    phone, templateCode, config.getSignName(), config.getRegionId(), params, requestId);

            // ---- 模拟 API 调用 (桩实现) ----
            // 真实实现示例:
            //   DefaultProfile profile = DefaultProfile.getProfile(config.getRegionId(), config.getAccessKeyId(), config.getAccessKeySecret());
            //   IAcsClient client = new DefaultAcsClient(profile);
            //   CommonRequest request = new CommonRequest();
            //   request.setSysAction("SendSms");
            //   request.putQueryParameter("PhoneNumbers", phone);
            //   request.putQueryParameter("SignName", config.getSignName());
            //   request.putQueryParameter("TemplateCode", templateCode);
            //   request.putQueryParameter("TemplateParam", JsonUtil.toJson(params));
            //   CommonResponse response = client.getCommonResponse(request);
            //   // 解析 response 获取结果

            return SmsResult.success(requestId);
        } catch (SmsException e) {
            // 直接重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("[阿里云短信] 发送异常 | phone={} | templateCode={} | requestId={}", phone, templateCode, requestId, e);
            throw new SmsException("阿里云短信发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量发送短信。
     *
     * <p>模拟阿里云短信批量发送 API 调用流程。
     * 真实场景中应根据手机号数量选择合适的策略：
     * 小批量使用循环单发，大批量使用批量 API。</p>
     *
     * @param phones       目标手机号列表
     * @param templateCode 短信模板编码
     * @param params       模板变量映射
     * @return 短信发送结果
     * @throws SmsException 参数校验失败或发送异常时抛出
     */
    @Override
    public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
        // ---- 参数校验 ----
        if (phones == null || phones.isEmpty()) {
            throw new SmsException("手机号列表不能为空");
        }
        if (templateCode == null || templateCode.isBlank()) {
            throw new SmsException("短信模板编码不能为空");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");

        try {
            // ---- 日志记录 ----
            log.info("[阿里云短信] 批量发送短信 | phones={} | templateCode={} | signName={} | regionId={} | params={} | requestId={}",
                    phones, templateCode, config.getSignName(), config.getRegionId(), params, requestId);

            return SmsResult.success(requestId);
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            log.error("[阿里云短信] 批量发送异常 | phones={} | templateCode={} | requestId={}", phones, templateCode, requestId, e);
            throw new SmsException("阿里云批量短信发送失败: " + e.getMessage(), e);
        }
    }

    // ======================== 提供商标识 ========================

    /**
     * 获取提供者名称。
     *
     * @return 固定返回 {@code "aliyun"}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
