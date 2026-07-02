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
 * <p>腾讯云短信服务提供者 — 基于腾讯云 SMS SDK 的短信发送实现。</p>
 *
 * <h3>职责</h3>
 * <p>封装腾讯云短信服务的 API 调用逻辑，将上层统一的 {@link SmsProvider} 接口
 * 转换为对腾讯云 SMS API 的具体调用。</p>
 *
 * <h3>实现说明</h3>
 * <ul>
 *   <li>当前为<strong>桩实现（Stub）</strong>，即不依赖真实的腾讯云 SMS SDK，
 *       而是模拟 API 调用的完整流程并记录日志。</li>
 *   <li>在生产环境中，替换为真实的 SDK 调用即可，接口保持完全兼容。</li>
 *   <li>单条和批量发送均构造唯一的 {@code requestId}，模拟 API 返回。</li>
 * </ul>
 *
 * <h3>日志策略</h3>
 * <p>所有发送的短信内容均在 <b>INFO</b> 级别记录，便于开发环境追踪和审计。</p>
 *
 * <h3>配置依赖</h3>
 * <p>需要配置 {@code letool.sms.tencent} 下的 {@code secretId}、{@code secretKey}、
 * {@code appId} 和 {@code signName} 四项参数。</p>
 *
 * <h3>与阿里云的差异</h3>
 * <p>腾讯云使用 {@code secretId} + {@code secretKey} 进行鉴权（而非阿里云的
 * {@code accessKeyId} + {@code accessKeySecret}），且使用 {@code appId}
 * 标识应用（而非阿里云的 {@code regionId} 标识区域）。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class TencentSmsProvider implements SmsProvider {

    // ======================== 常量与成员变量 ========================

    private static final Logger log = LoggerFactory.getLogger(TencentSmsProvider.class);

    /** 提供商标识 */
    public static final String PROVIDER_NAME = "tencent";

    /** 腾讯云短信配置 */
    private final SmsProperties.Tencent config;

    // ======================== 构造方法 ========================

    /**
     * 使用腾讯云短信配置构造提供者实例。
     *
     * @param config 腾讯云短信配置属性
     */
    public TencentSmsProvider(SmsProperties.Tencent config) {
        this.config = config;
        log.warn("TencentSmsProvider initialized in STUB mode - no real Tencent SMS API calls will be made.");
    }

    // ======================== 短信发送实现 ========================

    /**
     * 发送单条短信。
     *
     * <p>模拟腾讯云 SMS API 调用流程：参数校验、构造请求、调用 API、解析响应。</p>
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
            log.info("[腾讯云短信] 发送单条短信 | phone={} | templateCode={} | appId={} | signName={} | params={} | requestId={}",
                    phone, templateCode, config.getAppId(), config.getSignName(), params, requestId);

            // ---- 模拟 API 调用 (桩实现) ----
            // 真实实现示例:
            //   Credential cred = new Credential(config.getSecretId(), config.getSecretKey());
            //   SmsClient client = new SmsClient(cred, "ap-guangzhou");
            //   SendSmsRequest req = new SendSmsRequest();
            //   req.setSmsSdkAppId(config.getAppId());
            //   req.setSignName(config.getSignName());
            //   req.setTemplateID(templateCode);
            //   req.setPhoneNumberSet(new String[]{phone});
            //   req.setTemplateParamSet(new String[]{JsonUtil.toJson(params)});
            //   SendSmsResponse resp = client.SendSms(req);
            //   // 解析 response 获取结果

            return SmsResult.success(requestId);
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            log.error("[腾讯云短信] 发送异常 | phone={} | templateCode={} | requestId={}", phone, templateCode, requestId, e);
            throw new SmsException("腾讯云短信发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量发送短信。
     *
     * <p>模拟腾讯云短信批量发送 API 调用流程。
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
            log.info("[腾讯云短信] 批量发送短信 | phones={} | templateCode={} | appId={} | signName={} | params={} | requestId={}",
                    phones, templateCode, config.getAppId(), config.getSignName(), params, requestId);

            return SmsResult.success(requestId);
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            log.error("[腾讯云短信] 批量发送异常 | phones={} | templateCode={} | requestId={}", phones, templateCode, requestId, e);
            throw new SmsException("腾讯云批量短信发送失败: " + e.getMessage(), e);
        }
    }

    // ======================== 提供商标识 ========================

    /**
     * 获取提供者名称。
     *
     * @return 固定返回 {@code "tencent"}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
