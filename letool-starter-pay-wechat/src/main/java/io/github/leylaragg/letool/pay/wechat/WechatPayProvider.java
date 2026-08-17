package io.github.leylaragg.letool.pay.wechat;

import io.github.leylaragg.letool.pay.core.PayProvider;
import io.github.leylaragg.letool.pay.exception.PayErrorCode;
import io.github.leylaragg.letool.pay.exception.PayException;
import io.github.leylaragg.letool.pay.model.PayAction;
import io.github.leylaragg.letool.pay.model.PayActionType;
import io.github.leylaragg.letool.pay.model.PayCloseRequest;
import io.github.leylaragg.letool.pay.model.PayNotification;
import io.github.leylaragg.letool.pay.model.PayNotificationRequest;
import io.github.leylaragg.letool.pay.model.PayQueryRequest;
import io.github.leylaragg.letool.pay.model.PayRequest;
import io.github.leylaragg.letool.pay.model.PayResponse;
import io.github.leylaragg.letool.pay.model.PayStatus;
import io.github.leylaragg.letool.pay.model.RefundQueryRequest;
import io.github.leylaragg.letool.pay.model.RefundRequest;
import io.github.leylaragg.letool.pay.model.RefundResponse;
import io.github.leylaragg.letool.pay.model.RefundStatus;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByIdRequest;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 基于微信支付 V3 官方 SDK 的生产级支付 Provider。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class WechatPayProvider implements PayProvider {

    private static final String PROVIDER = "wechat";
    private static final String DEFAULT_SIGN_TYPE = "WECHATPAY2-SHA256-RSA2048";

    private final WechatPaySdk sdk;
    private final WechatPayProperties properties;

    /**
     * 创建微信支付 Provider。
     *
     * @param sdk 官方 SDK 调用边界
     * @param properties 微信支付配置
     */
    WechatPayProvider(WechatPaySdk sdk, WechatPayProperties properties) {
        this.sdk = require(sdk, "WechatPaySdk");
        this.properties = require(properties, "WechatPayProperties");
    }

    /** {@inheritDoc} */
    @Override
    public String getProviderName() { return PROVIDER; }

    /** {@inheritDoc} */
    @Override
    public PayResponse create(PayRequest request) {
        return switch (request.getScene()) {
            case QR_CODE -> createNative(request);
            case WAP -> createH5(request);
            case APP -> createApp(request);
            case JSAPI -> createJsapi(request);
            case PAGE -> throw PayException.of(PayErrorCode.REQUEST_INVALID,
                    "微信支付不支持 PAGE 场景");
        };
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse query(PayQueryRequest request) {
        Transaction transaction;
        if (!blank(request.getTransactionId())) {
            QueryOrderByIdRequest sdkRequest = new QueryOrderByIdRequest();
            sdkRequest.setTransactionId(request.getTransactionId());
            sdkRequest.setMchid(properties.getMchId());
            transaction = execute(() -> sdk.queryById(sdkRequest));
        } else {
            QueryOrderByOutTradeNoRequest sdkRequest = new QueryOrderByOutTradeNoRequest();
            sdkRequest.setOutTradeNo(request.getOutTradeNo());
            sdkRequest.setMchid(properties.getMchId());
            transaction = execute(() -> sdk.queryByOutTradeNo(sdkRequest));
        }
        return payResponse(transaction);
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse close(PayCloseRequest request) {
        if (blank(request.getOutTradeNo())) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID,
                    "微信支付关单必须提供商户订单号");
        }
        CloseOrderRequest sdkRequest = new CloseOrderRequest();
        sdkRequest.setOutTradeNo(request.getOutTradeNo());
        sdkRequest.setMchid(properties.getMchId());
        execute(() -> {
            sdk.close(sdkRequest);
            return null;
        });
        return PayResponse.builder().provider(PROVIDER).outTradeNo(request.getOutTradeNo())
                .status(PayStatus.CLOSED).build();
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse refund(RefundRequest request) {
        if (request.getTotalAmount() == null) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID,
                    "微信支付退款必须提供原订单金额 totalAmount");
        }
        CreateRequest sdkRequest = new CreateRequest();
        sdkRequest.setOutTradeNo(request.getOutTradeNo());
        sdkRequest.setTransactionId(request.getTransactionId());
        sdkRequest.setOutRefundNo(request.getOutRefundNo());
        sdkRequest.setReason(request.getReason());
        sdkRequest.setNotifyUrl(firstNonBlank(request.getNotifyUrl(), properties.getNotifyUrl()));
        AmountReq amount = new AmountReq();
        amount.setRefund(toLongCents(request.getAmount(), "退款金额"));
        amount.setTotal(toLongCents(request.getTotalAmount(), "原订单金额"));
        amount.setCurrency(request.getCurrency());
        sdkRequest.setAmount(amount);
        return refundResponse(execute(() -> sdk.createRefund(sdkRequest)));
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse queryRefund(RefundQueryRequest request) {
        QueryByOutRefundNoRequest sdkRequest = new QueryByOutRefundNoRequest();
        sdkRequest.setOutRefundNo(request.getOutRefundNo());
        return refundResponse(execute(() -> sdk.queryRefund(sdkRequest)));
    }

    /** {@inheritDoc} */
    @Override
    public PayNotification parseNotification(PayNotificationRequest request) {
        String serial = requiredHeader(request, "Wechatpay-Serial");
        String timestamp = requiredHeader(request, "Wechatpay-Timestamp");
        String nonce = requiredHeader(request, "Wechatpay-Nonce");
        String signature = requiredHeader(request, "Wechatpay-Signature");
        if (blank(request.getRawBody())) {
            throw PayException.of(PayErrorCode.NOTIFICATION_INVALID, PROVIDER, "回调原始正文缺失");
        }
        String signType = firstNonBlank(request.getHeader("Wechatpay-Signature-Type"), DEFAULT_SIGN_TYPE);
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial).timestamp(timestamp).nonce(nonce)
                .signature(signature).signType(signType).body(request.getRawBody()).build();
        final Transaction transaction;
        try {
            // 官方 NotificationParser 在此处同时完成签名验证和资源解密。
            transaction = sdk.parseNotification(requestParam);
        } catch (RuntimeException exception) {
            throw PayException.causedBy(PayErrorCode.SIGNATURE_INVALID, exception, PROVIDER);
        }
        if (transaction == null || blank(transaction.getOutTradeNo()) || transaction.getTradeState() == null) {
            throw PayException.of(PayErrorCode.NOTIFICATION_INVALID, PROVIDER, "解密后的交易字段不完整");
        }
        TransactionAmount amount = transaction.getAmount();
        return PayNotification.builder().provider(PROVIDER)
                .eventType(transaction.getTradeState().name())
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .amount(amount == null ? null : fromCents(amount.getTotal()))
                .currency(amount == null ? "CNY" : amount.getCurrency())
                .status(payStatus(transaction.getTradeState()))
                .completedAt(parseTime(transaction.getSuccessTime()))
                .build();
    }

    private PayResponse createNative(PayRequest request) {
        com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest sdkRequest =
                new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        setNativeFields(sdkRequest, request);
        com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse response =
                execute(() -> sdk.nativePrepay(sdkRequest));
        String codeUrl = requiredResponse(response == null ? null : response.getCodeUrl(), "code_url");
        return creationResponse(request, PayAction.of(PayActionType.QR_CODE_URL,
                Map.of("codeUrl", codeUrl)));
    }

    private PayResponse createH5(PayRequest request) {
        if (blank(request.getClientIp())) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "微信 H5 支付必须提供 clientIp");
        }
        com.wechat.pay.java.service.payments.h5.model.PrepayRequest sdkRequest =
                new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        setH5Fields(sdkRequest, request);
        com.wechat.pay.java.service.payments.h5.model.PrepayResponse response =
                execute(() -> sdk.h5Prepay(sdkRequest));
        String h5Url = requiredResponse(response == null ? null : response.getH5Url(), "h5_url");
        return creationResponse(request, PayAction.of(PayActionType.REDIRECT_URL,
                Map.of("redirectUrl", h5Url)));
    }

    private PayResponse createApp(PayRequest request) {
        com.wechat.pay.java.service.payments.app.model.PrepayRequest sdkRequest =
                new com.wechat.pay.java.service.payments.app.model.PrepayRequest();
        setAppFields(sdkRequest, request);
        com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse response =
                execute(() -> sdk.appPrepay(sdkRequest));
        Map<String, String> parameters = new LinkedHashMap<>();
        putRequired(parameters, "appId", response == null ? null : response.getAppid());
        putRequired(parameters, "partnerId", response.getPartnerId());
        putRequired(parameters, "prepayId", response.getPrepayId());
        putRequired(parameters, "package", response.getPackageVal());
        putRequired(parameters, "nonceStr", response.getNonceStr());
        putRequired(parameters, "timestamp", response.getTimestamp());
        putRequired(parameters, "sign", response.getSign());
        return creationResponse(request, PayAction.of(PayActionType.APP_ORDER_STRING, parameters));
    }

    private PayResponse createJsapi(PayRequest request) {
        if (blank(request.getPayerId())) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "微信 JSAPI 支付必须提供 payerId（openid）");
        }
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest sdkRequest =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        setJsapiFields(sdkRequest, request);
        com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse response =
                execute(() -> sdk.jsapiPrepay(sdkRequest));
        Map<String, String> parameters = new LinkedHashMap<>();
        putRequired(parameters, "appId", response == null ? null : response.getAppId());
        putRequired(parameters, "timeStamp", response.getTimeStamp());
        putRequired(parameters, "nonceStr", response.getNonceStr());
        putRequired(parameters, "package", response.getPackageVal());
        putRequired(parameters, "signType", response.getSignType());
        putRequired(parameters, "paySign", response.getPaySign());
        return creationResponse(request, PayAction.of(PayActionType.JSAPI_PARAMETERS, parameters));
    }

    private void setNativeFields(
            com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest target,
            PayRequest request) {
        target.setAppid(properties.getAppId()); target.setMchid(properties.getMchId());
        target.setDescription(request.getSubject()); target.setOutTradeNo(request.getOutTradeNo());
        target.setTimeExpire(formatTime(request.getExpiresAt())); target.setAttach(request.getMetadata().get("attach"));
        target.setNotifyUrl(notifyUrl(request));
        com.wechat.pay.java.service.payments.nativepay.model.Amount amount =
                new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(toIntCents(request.getAmount(), "支付金额")); amount.setCurrency(request.getCurrency());
        target.setAmount(amount);
    }

    private void setH5Fields(
            com.wechat.pay.java.service.payments.h5.model.PrepayRequest target,
            PayRequest request) {
        validateH5Configuration();
        target.setAppid(properties.getAppId()); target.setMchid(properties.getMchId());
        target.setDescription(request.getSubject()); target.setOutTradeNo(request.getOutTradeNo());
        target.setTimeExpire(formatTime(request.getExpiresAt())); target.setAttach(request.getMetadata().get("attach"));
        target.setNotifyUrl(notifyUrl(request));
        com.wechat.pay.java.service.payments.h5.model.Amount amount =
                new com.wechat.pay.java.service.payments.h5.model.Amount();
        amount.setTotal(toIntCents(request.getAmount(), "支付金额")); amount.setCurrency(request.getCurrency());
        target.setAmount(amount);
        com.wechat.pay.java.service.payments.h5.model.H5Info h5Info =
                new com.wechat.pay.java.service.payments.h5.model.H5Info();
        h5Info.setType(properties.getH5Type()); h5Info.setAppName(properties.getH5AppName());
        h5Info.setAppUrl(properties.getH5AppUrl());
        h5Info.setBundleId(properties.getH5BundleId());
        h5Info.setPackageName(properties.getH5PackageName());
        com.wechat.pay.java.service.payments.h5.model.SceneInfo sceneInfo =
                new com.wechat.pay.java.service.payments.h5.model.SceneInfo();
        sceneInfo.setPayerClientIp(request.getClientIp()); sceneInfo.setH5Info(h5Info);
        target.setSceneInfo(sceneInfo);
    }

    private void setAppFields(
            com.wechat.pay.java.service.payments.app.model.PrepayRequest target,
            PayRequest request) {
        target.setAppid(properties.getAppId()); target.setMchid(properties.getMchId());
        target.setDescription(request.getSubject()); target.setOutTradeNo(request.getOutTradeNo());
        target.setTimeExpire(formatTime(request.getExpiresAt())); target.setAttach(request.getMetadata().get("attach"));
        target.setNotifyUrl(notifyUrl(request));
        com.wechat.pay.java.service.payments.app.model.Amount amount =
                new com.wechat.pay.java.service.payments.app.model.Amount();
        amount.setTotal(toIntCents(request.getAmount(), "支付金额")); amount.setCurrency(request.getCurrency());
        target.setAmount(amount);
    }

    private void setJsapiFields(
            com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest target,
            PayRequest request) {
        target.setAppid(properties.getAppId()); target.setMchid(properties.getMchId());
        target.setDescription(request.getSubject()); target.setOutTradeNo(request.getOutTradeNo());
        target.setTimeExpire(formatTime(request.getExpiresAt())); target.setAttach(request.getMetadata().get("attach"));
        target.setNotifyUrl(notifyUrl(request));
        com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(toIntCents(request.getAmount(), "支付金额")); amount.setCurrency(request.getCurrency());
        target.setAmount(amount);
        com.wechat.pay.java.service.payments.jsapi.model.Payer payer =
                new com.wechat.pay.java.service.payments.jsapi.model.Payer();
        payer.setOpenid(request.getPayerId()); target.setPayer(payer);
    }

    private PayResponse creationResponse(PayRequest request, PayAction action) {
        return PayResponse.builder().provider(PROVIDER).outTradeNo(request.getOutTradeNo())
                .status(PayStatus.PENDING).action(action).build();
    }

    private PayResponse payResponse(Transaction transaction) {
        if (transaction == null) {
            throw PayException.of(PayErrorCode.PROVIDER_REJECTED, PROVIDER, "微信支付返回空订单");
        }
        return PayResponse.builder().provider(PROVIDER).outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId()).status(payStatus(transaction.getTradeState()))
                .platformMessage(transaction.getTradeStateDesc()).build();
    }

    private RefundResponse refundResponse(Refund refund) {
        if (refund == null || blank(refund.getOutRefundNo())) {
            throw PayException.of(PayErrorCode.PROVIDER_REJECTED, PROVIDER, "微信支付返回空退款单");
        }
        return RefundResponse.builder().provider(PROVIDER).outTradeNo(refund.getOutTradeNo())
                .transactionId(refund.getTransactionId()).outRefundNo(refund.getOutRefundNo())
                .refundId(refund.getRefundId()).status(refundStatus(refund.getStatus())).build();
    }

    private PayStatus payStatus(Transaction.TradeStateEnum state) {
        if (state == null) { return PayStatus.UNKNOWN; }
        return switch (state) {
            case SUCCESS, REFUND -> PayStatus.SUCCESS;
            case NOTPAY, USERPAYING, ACCEPT -> PayStatus.PENDING;
            case CLOSED, REVOKED -> PayStatus.CLOSED;
            case PAYERROR -> PayStatus.FAILED;
        };
    }

    private RefundStatus refundStatus(Status status) {
        if (status == null) { return RefundStatus.UNKNOWN; }
        return switch (status) {
            case SUCCESS -> RefundStatus.SUCCESS;
            case PROCESSING -> RefundStatus.PROCESSING;
            case CLOSED -> RefundStatus.CLOSED;
            case ABNORMAL -> RefundStatus.FAILED;
        };
    }

    private <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (PayException exception) {
            throw exception;
        } catch (ServiceException exception) {
            throw PayException.causedBy(PayErrorCode.PROVIDER_REJECTED, exception,
                    PROVIDER, exception.getErrorCode() + " " + exception.getErrorMessage());
        } catch (RuntimeException exception) {
            throw PayException.causedBy(PayErrorCode.OPERATION_FAILED, exception, PROVIDER);
        }
    }

    private String requiredHeader(PayNotificationRequest request, String name) {
        String value = request.getHeader(name);
        if (blank(value)) {
            throw PayException.of(PayErrorCode.NOTIFICATION_INVALID, PROVIDER, name + " 请求头缺失");
        }
        return value;
    }

    private void validateH5Configuration() {
        String type = properties.getH5Type();
        if (blank(properties.getH5AppName())) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "微信 H5 支付必须配置 letool.pay.wechat.h5-app-name");
        }
        if ("Wap".equalsIgnoreCase(type) && blank(properties.getH5AppUrl())) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "Wap 场景必须配置 letool.pay.wechat.h5-app-url");
        }
        if ("iOS".equalsIgnoreCase(type) && blank(properties.getH5BundleId())) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "iOS 场景必须配置 letool.pay.wechat.h5-bundle-id");
        }
        if ("Android".equalsIgnoreCase(type) && blank(properties.getH5PackageName())) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "Android 场景必须配置 letool.pay.wechat.h5-package-name");
        }
        if (!"Wap".equalsIgnoreCase(type)
                && !"iOS".equalsIgnoreCase(type)
                && !"Android".equalsIgnoreCase(type)) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "letool.pay.wechat.h5-type 仅支持 Wap、iOS 或 Android");
        }
    }

    private String requiredResponse(String value, String name) {
        if (blank(value)) {
            throw PayException.of(PayErrorCode.PROVIDER_REJECTED, PROVIDER, name + " 响应字段缺失");
        }
        return value;
    }

    private void putRequired(Map<String, String> target, String name, String value) {
        target.put(name, requiredResponse(value, name));
    }

    private String notifyUrl(PayRequest request) {
        String value = firstNonBlank(request.getNotifyUrl(), properties.getNotifyUrl());
        if (blank(value)) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID,
                    "微信支付必须通过请求或 letool.pay.wechat.notify-url 提供通知地址");
        }
        return value;
    }

    private int toIntCents(BigDecimal amount, String fieldName) {
        try {
            return amount.movePointRight(2).intValueExact();
        } catch (ArithmeticException exception) {
            throw PayException.causedBy(PayErrorCode.REQUEST_INVALID, exception,
                    fieldName + "超出微信支付整数分范围");
        }
    }

    private long toLongCents(BigDecimal amount, String fieldName) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw PayException.causedBy(PayErrorCode.REQUEST_INVALID, exception,
                    fieldName + "超出微信支付整数分范围");
        }
    }

    private BigDecimal fromCents(Integer amount) {
        return amount == null ? null : BigDecimal.valueOf(amount, 2);
    }

    private String formatTime(Instant time) {
        return time == null ? null : DateTimeFormatter.ISO_INSTANT.format(time);
    }

    private Instant parseTime(String value) {
        if (blank(value)) { return null; }
        try {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value, Instant::from);
        } catch (DateTimeException exception) {
            throw PayException.causedBy(PayErrorCode.NOTIFICATION_INVALID,
                    exception, PROVIDER, "支付完成时间格式错误");
        }
    }

    private String firstNonBlank(String first, String second) {
        return blank(first) ? second : first;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T require(T value, String name) {
        if (value == null) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID, name + " 不能为空");
        }
        return value;
    }
}
