package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ExecutionEngineTest {

    private ExecutionEngine executionEngine;
    private MockBrokerAdapter mockBroker;
    private RiskManagementEngine riskEngine;

    @Before
    public void setUp() {
        mockBroker = new MockBrokerAdapter();
        riskEngine = new RiskManagementEngine();
        executionEngine = new ExecutionEngine(mockBroker, riskEngine);
        KillSwitch.resetMemoryState();
    }

    @Test
    public void testLiveTradingIsDisabledConstant() {
        assertFalse("Live Trading must be strictly DISABLED", ExecutionEngine.LIVE_TRADING_ENABLED);
        assertFalse("MockBrokerAdapter must not support live trading", mockBroker.isLiveTradingSupported());
    }

    @Test
    public void testExecuteMarketBuyOrderSuccess() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.FILLED, result.status);
        assertEquals(2650.0, result.fillPrice, 0.001);
        assertTrue(result.riskAmount > 0);
        assertEquals(2.0, result.riskRewardRatio, 0.01);
        assertEquals(ExecutionOrder.TradingMode.PAPER_TRADING, result.tradingMode);
    }

    @Test
    public void testExecuteMarketSellOrderSuccess() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.SELL, ExecutionOrder.OrderType.MARKET, 2650.0, 2660.0, 2630.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.FILLED, result.status);
        assertEquals(2650.0, result.fillPrice, 0.001);
        assertTrue(result.riskAmount > 0);
        assertEquals(2.0, result.riskRewardRatio, 0.01);
    }

    @Test
    public void testExecuteLimitOrderPendingState() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.PENDING, result.status);
        assertEquals(0.0, result.fillPrice, 0.001);

        // Evaluate market tick to trigger limit order fill
        executionEngine.processMarketTick(2635.0, null);
        assertEquals(ExecutionOrder.OrderStatus.FILLED, order.status);
        assertEquals(2635.0, order.fillPrice, 0.001);
    }

    @Test
    public void testExecuteStopOrderPendingState() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.STOP, 2660.0, 2650.0, 2680.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.PENDING, result.status);

        // Evaluate market tick to trigger stop order fill
        executionEngine.processMarketTick(2665.0, null);
        assertEquals(ExecutionOrder.OrderStatus.FILLED, order.status);
    }

    @Test
    public void testRejectionWhenLiveTradingRequested() {
        ExecutionOrder liveOrder = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        liveOrder.tradingMode = ExecutionOrder.TradingMode.LIVE_TRADING;

        ExecutionOrder result = executionEngine.executeOrder(liveOrder, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("LIVE TRADING IS DISABLED"));
    }

    @Test
    public void testRejectionWhenKillSwitchActive() {
        KillSwitch.activate(null);

        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("Kill Switch"));
    }

    @Test
    public void testRejectionByRiskManagementDueToMaxDailyLoss() {
        // Daily loss limit is 300 (3% of 10000). Current daily loss is 350.
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 350.0, null);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("الحد الأقصى للخسارة اليومية"));
    }

    @Test
    public void testRejectionByRiskManagementDueToBadSLTP() {
        // BUY order where SL is above Entry Price
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2660.0, 2680.0, 0.1);

        ExecutionOrder result = executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("أقل من سعر الدخول"));
    }

    @Test
    public void testCancelOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);
        executionEngine.executeOrder(order, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.PENDING, order.status);

        ExecutionOrder cancelled = executionEngine.cancelOrder(order.orderId, null);
        assertEquals(ExecutionOrder.OrderStatus.CANCELLED, cancelled.status);
    }

    @Test
    public void testModifyOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);
        executionEngine.executeOrder(order, 10000.0, 0.0, null);

        ExecutionOrder modified = executionEngine.modifyOrder(order.orderId, 2642.0, 2632.0, 2665.0, null);
        assertEquals(2642.0, modified.price, 0.001);
        assertEquals(2632.0, modified.stopLoss, 0.001);
        assertEquals(2665.0, modified.takeProfit, 0.001);
    }

    @Test
    public void testExecuteFromTradeSetup() {
        TradeSetup setup = new TradeSetup();
        setup.valid = true;
        setup.direction = TradeSetup.Direction.BUY;
        setup.entryPrice = 2650.0;
        setup.stopLoss = 2640.0;
        setup.takeProfit = 2670.0;
        setup.riskRewardRatio = 2.0;

        ExecutionOrder result = executionEngine.executeFromTradeSetup(setup, ExecutionOrder.OrderType.MARKET, 10000.0, 0.0, null);

        assertEquals(ExecutionOrder.OrderStatus.FILLED, result.status);
        assertEquals(2650.0, result.fillPrice, 0.001);
        assertEquals("TradeSetup Engine", result.signalSource);
    }

    @Test
    public void testNullOrderHandling() {
        ExecutionOrder result = executionEngine.executeOrder(null, 10000.0, 0.0, null);
        assertNotNull(result);
        assertEquals(ExecutionOrder.OrderStatus.FAILED, result.status);
    }
}
