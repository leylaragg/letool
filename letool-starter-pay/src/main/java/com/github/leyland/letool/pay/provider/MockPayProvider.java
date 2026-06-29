package com.github.leyland.letool.pay.provider;

import com.github.leyland.letool.pay.core.PayProvider;
import com.github.leyland.letool.pay.model.PayOrder;
import com.github.leyland.letool.pay.model.PayResult;
import com.github.leyland.letool.pay.model.PayStatus;
import com.github.leyland.letool.pay.model.RefundOrder;
import com.github.leyland.letool.tool.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mock 支付提供者，用于开发和测试环境。
 *
 * <p>该实现不会真正发起支付请求，所有操作均返回模拟的成功结果。
 * 同时维护一个操作历史列表，供测试用例验证调用行为。</p>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>单元测试：通过操作历史验证支付流程是否被正确调用</li>
 *   <li>本地开发：无需真实支付配置即可调试业务逻辑</li>
 *   <li>集成测试：模拟各种支付状态（可扩展）</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class MockPayProvider implements PayProvider {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(MockPayProvider.class);

    // ======================== 字段 ========================

    /** 操作历史记录，记录所有调用日志，供测试验证 */
    private final List<String> operationHistory = new ArrayList<>();

    // ======================== PayProvider 实现 ========================

    /**
     * 模拟支付下单。
     *
     * <p>生成一个模拟交易流水号并返回成功结果。同时记录操作日志。</p>
     *
     * @param order 支付订单
     * @return 模拟的成功支付结果
     */
    @Override
    public PayResult pay(PayOrder order) {
        String transactionId = IdUtil.simpleUUID();
        log.info("[MockPay] 模拟支付下单 → outTradeNo={}, subject={}, amount={}元, transactionId={}",
                order.getOutTradeNo(), order.getSubject(), order.getTotalAmount(), transactionId);
        record("PAY: " + order.getOutTradeNo() + " → " + transactionId);
        return new PayResult(true, order.getOutTradeNo(), transactionId,
                PayStatus.SUCCESS, order.getTotalAmount(), transactionId,
                null, null,
                Collections.singletonMap("mock", true));
    }

    /**
     * 模拟订单查询。
     *
     * <p>始终返回成功状态，模拟订单已支付。</p>
     *
     * @param outTradeNo 商户订单号
     * @return 模拟的查询结果（成功）
     */
    @Override
    public PayResult query(String outTradeNo) {
        String transactionId = IdUtil.simpleUUID();
        log.info("[MockPay] 模拟订单查询 → outTradeNo={}, status=SUCCESS", outTradeNo);
        record("QUERY: " + outTradeNo + " → SUCCESS");
        return PayResult.success(outTradeNo, transactionId, null);
    }

    /**
     * 模拟退款。
     *
     * <p>始终返回退款成功。</p>
     *
     * @param refundOrder 退款订单
     * @return 模拟的退款成功结果
     */
    @Override
    public PayResult refund(RefundOrder refundOrder) {
        String transactionId = IdUtil.simpleUUID();
        log.info("[MockPay] 模拟退款 → outTradeNo={}, refundNo={}, amount={}元",
                refundOrder.getOutTradeNo(), refundOrder.getOutRefundNo(), refundOrder.getRefundAmount());
        record("REFUND: " + refundOrder.getOutRefundNo() + " → " + transactionId);
        return new PayResult(true, refundOrder.getOutTradeNo(), transactionId,
                PayStatus.REFUND, refundOrder.getRefundAmount(), transactionId,
                null, null,
                Collections.singletonMap("mock", true));
    }

    /**
     * 模拟退款状态查询。
     *
     * @param refundNo 退款单号
     * @return 模拟的退款成功结果
     */
    @Override
    public PayResult queryRefund(String refundNo) {
        log.info("[MockPay] 模拟退款查询 → refundNo={}", refundNo);
        record("QUERY_REFUND: " + refundNo + " → SUCCESS");
        return new PayResult(true, null, IdUtil.simpleUUID(),
                PayStatus.REFUND, null, null,
                null, null, null);
    }

    /**
     * 模拟签名验证。
     *
     * <p>Mock 实现始终返回 true，表示验签通过。</p>
     *
     * @param params 回调参数
     * @param sign   签名
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean verifySign(Map<String, String> params, String sign) {
        log.debug("[MockPay] 模拟验签（始终通过）");
        return true;
    }

    /**
     * 获取提供者名称。
     *
     * @return 固定返回 "MOCK"
     */
    @Override
    public String getProviderName() {
        return "MOCK";
    }

    // ======================== 测试辅助方法 ========================

    /**
     * 获取操作历史记录。
     *
     * <p>返回的是一个新列表，外部修改不会影响内部状态。
     * 可在测试中断言特定操作是否被执行。</p>
     *
     * @return 操作历史记录的副本
     */
    public List<String> getOperationHistory() {
        return new ArrayList<>(operationHistory);
    }

    /**
     * 清空操作历史记录。
     *
     * <p>一般在每个测试用例开始前调用，保证测试隔离。</p>
     */
    public void clearHistory() {
        operationHistory.clear();
    }

    /**
     * 查询历史中是否包含指定关键词。
     *
     * @param keyword 要查找的关键词
     * @return {@code true} 包含，{@code false} 不包含
     */
    public boolean historyContains(String keyword) {
        return operationHistory.stream().anyMatch(entry -> entry.contains(keyword));
    }

    // ======================== 私有方法 ========================

    /**
     * 记录一条操作历史。
     *
     * @param operation 操作描述
     */
    private void record(String operation) {
        operationHistory.add(operation);
    }
}
