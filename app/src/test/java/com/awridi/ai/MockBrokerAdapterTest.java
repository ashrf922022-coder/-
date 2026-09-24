package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class MockBrokerAdapterTest {

    private MockBrokerAdapter adapter;

    @Before
    public void setUp() {
        adapter = new MockBrokerAdapter();
    }

    @Test
    public void testBrokerNameAndLiveTradingFlag() {
        assertEquals("Mock/Demo Broker", adapter.getBrokerName());
        assertFalse(adapter.isLiveTradingSupported());
    }

    @Test
    public void testSubmitMarketOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        ExecutionOrder result = adapter.submitOrder(order);

        assertEquals(ExecutionOrder.OrderStatus.FILLED, result.status);
        assertEquals(2650.0, result.fillPrice, 0.001);
        assertNotNull(result.filledTimestamp);
        assertEquals("Mock/Demo Broker", result.brokerName);
    }

    @Test
    public void testSubmitLimitOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);
        ExecutionOrder result = adapter.submitOrder(order);

        assertEquals(ExecutionOrder.OrderStatus.PENDING, result.status);
        assertEquals(0.0, result.fillPrice, 0.001);
    }

    @Test
    public void testRejectLiveTradingOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        order.tradingMode = ExecutionOrder.TradingMode.LIVE_TRADING;

        ExecutionOrder result = adapter.submitOrder(order);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, result.status);
        assertTrue(result.rejectionReason.contains("LIVE TRADING IS DISABLED"));
    }

    @Test
    public void testCancelOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);
        adapter.submitOrder(order);

        ExecutionOrder cancelled = adapter.cancelOrder(order.orderId);
        assertEquals(ExecutionOrder.OrderStatus.CANCELLED, cancelled.status);
    }

    @Test
    public void testModifyPendingOrder() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);
        adapter.submitOrder(order);

        ExecutionOrder modified = adapter.modifyOrder(order.orderId, 2645.0, 2635.0, 2670.0);
        assertEquals(2645.0, modified.price, 0.001);
        assertEquals(2635.0, modified.stopLoss, 0.001);
        assertEquals(2670.0, modified.takeProfit, 0.001);
    }

    @Test
    public void testQueryOrderStatus() {
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        adapter.submitOrder(order);

        ExecutionOrder queried = adapter.queryOrderStatus(order.orderId);
        assertEquals(order.orderId, queried.orderId);
        assertEquals(ExecutionOrder.OrderStatus.FILLED, queried.status);
    }

    @Test
    public void testGetActiveOrders() {
        ExecutionOrder o1 = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2640.0, 2670.0, 0.1);
        ExecutionOrder o2 = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.LIMIT, 2640.0, 2630.0, 2660.0, 0.1);

        adapter.submitOrder(o1);
        adapter.submitOrder(o2);

        List<ExecutionOrder> active = adapter.getActiveOrders();
        assertEquals(2, active.size());
    }

    @Test
    public void testNullOrderSubmission() {
        ExecutionOrder res = adapter.submitOrder(null);
        assertNotNull(res);
        assertEquals(ExecutionOrder.OrderStatus.FAILED, res.status);
    }
}
