package com.github.leyland.letool.pay.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayAction;
import com.github.leyland.letool.pay.model.PayActionType;
import com.github.leyland.letool.pay.model.PayCloseRequest;
import com.github.leyland.letool.pay.model.PayNotification;
import com.github.leyland.letool.pay.model.PayNotificationRequest;
import com.github.leyland.letool.pay.model.PayQueryRequest;
import com.github.leyland.letool.pay.model.PayRequest;
import com.github.leyland.letool.pay.model.PayResponse;
import com.github.leyland.letool.pay.model.PayScene;
import com.github.leyland.letool.pay.model.PayStatus;
import com.github.leyland.letool.pay.model.RefundQueryRequest;
import com.github.leyland.letool.pay.model.RefundRequest;
import com.github.leyland.letool.pay.model.RefundResponse;
import com.github.leyland.letool.pay.model.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 基于支付宝官方 SDK 的生产级支付 Provider。
 *
 * @author leyland
 * @since 2.0.0
 */
public final class AlipayPayProvider implements PayProvider {

    private static final String PROVIDER = "alipay";
    private static final DateTimeFormatter ALIPAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ALIPAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final AlipayClient client;
    private final AlipayPayProperties properties;
    private final AlipayNotificationVerifier verifier;

    /**
     * 创建使用官方验签算法的支付宝 Provider。
     *
     * @param client 支付宝官方客户端
     * @param properties 支付宝配置
     */
    public AlipayPayProvider(AlipayClient client, AlipayPayProperties properties) {
        this(client, properties, AlipayNotificationVerifier.official());
    }

    AlipayPayProvider(
            AlipayClient client,
            AlipayPayProperties properties,
            AlipayNotificationVerifier verifier) {
        this.client = require(client, "AlipayClient");
        this.properties = require(properties, "AlipayPayProperties");
        this.verifier = require(verifier, "AlipayNotificationVerifier");
    }

    /** {@inheritDoc} */
    @Override
    public String getProviderName() { return PROVIDER; }

    /** {@inheritDoc} */
    @Override
    public PayResponse create(PayRequest request) {
        try {
            return switch (request.getScene()) {
                case PAGE -> createPage(request);
                case WAP -> createWap(request);
                case APP -> createApp(request);
                case QR_CODE -> createQrCode(request);
                case JSAPI -> throw PayException.of(PayErrorCode.REQUEST_INVALID,
                        "支付宝不支持 JSAPI 场景");
            };
        } catch (AlipayApiException exception) {
            throw sdkFailure(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse query(PayQueryRequest request) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        setPaymentIdentifier(model, request.getOutTradeNo(), request.getTransactionId());
        AlipayTradeQueryRequest sdkRequest = new AlipayTradeQueryRequest();
        sdkRequest.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(sdkRequest);
            ensureSuccess(response);
            return payResponse(response.getOutTradeNo(), response.getTradeNo(),
                    payStatus(response.getTradeStatus()), response);
        } catch (AlipayApiException exception) {
            throw sdkFailure(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PayResponse close(PayCloseRequest request) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        setPaymentIdentifier(model, request.getOutTradeNo(), request.getTransactionId());
        AlipayTradeCloseRequest sdkRequest = new AlipayTradeCloseRequest();
        sdkRequest.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(sdkRequest);
            ensureSuccess(response);
            return payResponse(response.getOutTradeNo(), response.getTradeNo(), PayStatus.CLOSED, response);
        } catch (AlipayApiException exception) {
            throw sdkFailure(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse refund(RefundRequest request) {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        setPaymentIdentifier(model, request.getOutTradeNo(), request.getTransactionId());
        model.setOutRequestNo(request.getOutRefundNo());
        model.setRefundAmount(request.getAmount().toPlainString());
        model.setRefundCurrency(request.getCurrency());
        model.setRefundReason(request.getReason());
        AlipayTradeRefundRequest sdkRequest = new AlipayTradeRefundRequest();
        sdkRequest.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(sdkRequest);
            ensureSuccess(response);
            return refundResponse(response.getOutTradeNo(), response.getTradeNo(),
                    request.getOutRefundNo(), RefundStatus.SUCCESS, response);
        } catch (AlipayApiException exception) {
            throw sdkFailure(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public RefundResponse queryRefund(RefundQueryRequest request) {
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutRequestNo(request.getOutRefundNo());
        AlipayTradeFastpayRefundQueryRequest sdkRequest = new AlipayTradeFastpayRefundQueryRequest();
        sdkRequest.setBizModel(model);
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(sdkRequest);
            ensureSuccess(response);
            return refundResponse(response.getOutTradeNo(), response.getTradeNo(),
                    blank(response.getOutRequestNo()) ? request.getOutRefundNo() : response.getOutRequestNo(),
                    refundStatus(response.getRefundStatus()), response);
        } catch (AlipayApiException exception) {
            throw sdkFailure(exception);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PayNotification parseNotification(PayNotificationRequest request) {
        Map<String, String> parameters = request.getFormParameters();
        if (parameters.isEmpty() || blank(parameters.get("sign"))) {
            throw PayException.of(PayErrorCode.NOTIFICATION_INVALID, PROVIDER, "签名或表单参数缺失");
        }
        try {
            if (!verifier.verify(parameters, properties)) {
                throw PayException.of(PayErrorCode.SIGNATURE_INVALID, PROVIDER);
            }
        } catch (AlipayApiException exception) {
            throw PayException.causedBy(PayErrorCode.SIGNATURE_INVALID, exception, PROVIDER);
        }

        String outTradeNo = requiredNotification(parameters, "out_trade_no");
        return PayNotification.builder()
                .provider(PROVIDER)
                .eventType(requiredNotification(parameters, "trade_status"))
                .outTradeNo(outTradeNo)
                .transactionId(parameters.get("trade_no"))
                .amount(decimal(parameters.get("total_amount")))
                .currency("CNY")
                .status(payStatus(parameters.get("trade_status")))
                .completedAt(parseTime(parameters.get("gmt_payment")))
                .notificationId(parameters.get("notify_id"))
                .build();
    }

    private PayResponse createPage(PayRequest request) throws AlipayApiException {
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        setCreateFields(model, request);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        AlipayTradePagePayRequest sdkRequest = new AlipayTradePagePayRequest();
        sdkRequest.setBizModel(model);
        setUrls(sdkRequest, request);
        AlipayTradePagePayResponse response = client.pageExecute(sdkRequest);
        ensureSuccess(response);
        return creationResponse(request, response,
                PayAction.of(PayActionType.FORM_HTML,
                        Map.of("formHtml", requiredResponse(response.getBody(), "formHtml"))));
    }

    private PayResponse createWap(PayRequest request) throws AlipayApiException {
        if (blank(request.getReturnUrl())) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, "支付宝 WAP 支付必须提供 returnUrl");
        }
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        setCreateFields(model, request);
        model.setProductCode("QUICK_WAP_WAY");
        model.setQuitUrl(request.getReturnUrl());
        AlipayTradeWapPayRequest sdkRequest = new AlipayTradeWapPayRequest();
        sdkRequest.setBizModel(model);
        setUrls(sdkRequest, request);
        AlipayTradeWapPayResponse response = client.pageExecute(sdkRequest);
        ensureSuccess(response);
        return creationResponse(request, response,
                PayAction.of(PayActionType.FORM_HTML,
                        Map.of("formHtml", requiredResponse(response.getBody(), "formHtml"))));
    }

    private PayResponse createApp(PayRequest request) throws AlipayApiException {
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        setCreateFields(model, request);
        model.setProductCode("QUICK_MSECURITY_PAY");
        AlipayTradeAppPayRequest sdkRequest = new AlipayTradeAppPayRequest();
        sdkRequest.setBizModel(model);
        sdkRequest.setNotifyUrl(request.getNotifyUrl());
        AlipayTradeAppPayResponse response = client.sdkExecute(sdkRequest);
        ensureSuccess(response);
        return creationResponse(request, response,
                PayAction.of(PayActionType.APP_ORDER_STRING,
                        Map.of("orderString", requiredResponse(response.getBody(), "orderString"))));
    }

    private PayResponse createQrCode(PayRequest request) throws AlipayApiException {
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        setCreateFields(model, request);
        AlipayTradePrecreateRequest sdkRequest = new AlipayTradePrecreateRequest();
        sdkRequest.setBizModel(model);
        sdkRequest.setNotifyUrl(request.getNotifyUrl());
        AlipayTradePrecreateResponse response = client.execute(sdkRequest);
        ensureSuccess(response);
        return creationResponse(request, response,
                PayAction.of(PayActionType.QR_CODE_URL,
                        Map.of("codeUrl", requiredResponse(response.getQrCode(), "qrCode"))));
    }

    private void setCreateFields(Object model, PayRequest request) {
        String amount = request.getAmount().toPlainString();
        String expiresAt = request.getExpiresAt() == null ? null
                : ALIPAY_TIME.format(request.getExpiresAt().atZone(ALIPAY_ZONE));
        if (model instanceof AlipayTradePagePayModel target) {
            target.setOutTradeNo(request.getOutTradeNo()); target.setSubject(request.getSubject());
            target.setTotalAmount(amount); target.setTimeExpire(expiresAt);
        } else if (model instanceof AlipayTradeWapPayModel target) {
            target.setOutTradeNo(request.getOutTradeNo()); target.setSubject(request.getSubject());
            target.setTotalAmount(amount); target.setTimeExpire(expiresAt);
        } else if (model instanceof AlipayTradeAppPayModel target) {
            target.setOutTradeNo(request.getOutTradeNo()); target.setSubject(request.getSubject());
            target.setTotalAmount(amount); target.setTimeExpire(expiresAt);
        } else if (model instanceof AlipayTradePrecreateModel target) {
            target.setOutTradeNo(request.getOutTradeNo()); target.setSubject(request.getSubject());
            target.setTotalAmount(amount); target.setTimeExpire(expiresAt);
        }
    }

    private void setUrls(AlipayRequest<?> sdkRequest, PayRequest request) {
        if (sdkRequest instanceof AlipayTradePagePayRequest target) {
            target.setNotifyUrl(request.getNotifyUrl()); target.setReturnUrl(request.getReturnUrl());
        } else if (sdkRequest instanceof AlipayTradeWapPayRequest target) {
            target.setNotifyUrl(request.getNotifyUrl()); target.setReturnUrl(request.getReturnUrl());
        }
    }

    private void setPaymentIdentifier(AlipayTradeQueryModel model, String outTradeNo, String transactionId) {
        model.setOutTradeNo(outTradeNo); model.setTradeNo(transactionId);
    }

    private void setPaymentIdentifier(AlipayTradeCloseModel model, String outTradeNo, String transactionId) {
        model.setOutTradeNo(outTradeNo); model.setTradeNo(transactionId);
    }

    private void setPaymentIdentifier(AlipayTradeRefundModel model, String outTradeNo, String transactionId) {
        model.setOutTradeNo(outTradeNo); model.setTradeNo(transactionId);
    }

    private PayResponse creationResponse(PayRequest request, AlipayResponse response, PayAction action) {
        return PayResponse.builder().provider(PROVIDER).outTradeNo(request.getOutTradeNo())
                .status(PayStatus.PENDING).action(action).platformCode(code(response))
                .platformMessage(message(response)).build();
    }

    private PayResponse payResponse(
            String outTradeNo, String transactionId, PayStatus status, AlipayResponse response) {
        return PayResponse.builder().provider(PROVIDER).outTradeNo(outTradeNo)
                .transactionId(transactionId).status(status).platformCode(code(response))
                .platformMessage(message(response)).build();
    }

    private RefundResponse refundResponse(
            String outTradeNo, String transactionId, String outRefundNo,
            RefundStatus status, AlipayResponse response) {
        return RefundResponse.builder().provider(PROVIDER).outTradeNo(outTradeNo)
                .transactionId(transactionId).outRefundNo(outRefundNo).status(status)
                .platformCode(code(response)).platformMessage(message(response)).build();
    }

    private void ensureSuccess(AlipayResponse response) {
        if (response == null || !response.isSuccess()) {
            String code = response == null ? "EMPTY_RESPONSE" : code(response);
            String message = response == null ? "支付宝返回空响应" : message(response);
            throw PayException.of(PayErrorCode.PROVIDER_REJECTED, PROVIDER, code + " " + message);
        }
    }

    private String code(AlipayResponse response) {
        return blank(response.getSubCode()) ? response.getCode() : response.getSubCode();
    }

    private String message(AlipayResponse response) {
        return blank(response.getSubMsg()) ? response.getMsg() : response.getSubMsg();
    }

    private PayStatus payStatus(String status) {
        if (status == null) { return PayStatus.UNKNOWN; }
        return switch (status) {
            case "WAIT_BUYER_PAY" -> PayStatus.PENDING;
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PayStatus.SUCCESS;
            case "TRADE_CLOSED" -> PayStatus.CLOSED;
            default -> PayStatus.UNKNOWN;
        };
    }

    private RefundStatus refundStatus(String status) {
        if (status == null) { return RefundStatus.UNKNOWN; }
        return switch (status) {
            case "REFUND_SUCCESS" -> RefundStatus.SUCCESS;
            case "REFUND_PROCESSING" -> RefundStatus.PROCESSING;
            case "REFUND_CLOSED" -> RefundStatus.CLOSED;
            case "REFUND_FAIL", "REFUND_FAILED" -> RefundStatus.FAILED;
            default -> RefundStatus.UNKNOWN;
        };
    }

    private String requiredNotification(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (blank(value)) {
            throw PayException.of(PayErrorCode.NOTIFICATION_INVALID, PROVIDER, name + " 缺失");
        }
        return value;
    }

    private String requiredResponse(String value, String name) {
        if (blank(value)) {
            throw PayException.of(PayErrorCode.PROVIDER_REJECTED,
                    PROVIDER, name + " 响应字段缺失");
        }
        return value;
    }

    private BigDecimal decimal(String value) {
        if (blank(value)) { return null; }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw PayException.causedBy(PayErrorCode.NOTIFICATION_INVALID,
                    exception, PROVIDER, "金额格式错误");
        }
    }

    private Instant parseTime(String value) {
        if (blank(value)) { return null; }
        try {
            return LocalDateTime.parse(value, ALIPAY_TIME).atZone(ALIPAY_ZONE).toInstant();
        } catch (DateTimeParseException exception) {
            throw PayException.causedBy(PayErrorCode.NOTIFICATION_INVALID,
                    exception, PROVIDER, "支付时间格式错误");
        }
    }

    private PayException sdkFailure(AlipayApiException exception) {
        return PayException.causedBy(PayErrorCode.OPERATION_FAILED, exception, PROVIDER);
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
