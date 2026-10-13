package com.awridi.ai;

import java.util.List;

/**
 * BrokerAdapter Interface
 * Provides an abstract layer for executing orders against a broker.
 * In Phase 7, only Mock/Demo Broker implementation is used.
 * Real broker connections are strictly DISABLED.
 */
public interface BrokerAdapter {

    /**
     * Indicates whether real live trading is supported by this adapter.
     * Always returns false for Mock/Demo Broker.
     */
    boolean isLiveTradingSupported();

    /**
     * Returns the name of the broker.
     */
    String getBrokerName();

    /**
     * Submits an order for processing by the broker.
     */
    ExecutionOrder submitOrder(ExecutionOrder order);

    /**
     * Cancels a pending order by ID.
     */
    ExecutionOrder cancelOrder(String orderId);

    /**
     * Modifies a pending or active order's price, SL, or TP.
     */
    ExecutionOrder modifyOrder(String orderId, double newPrice, double newStopLoss, double newTakeProfit);

    /**
     * Queries current status of an order.
     */
    ExecutionOrder queryOrderStatus(String orderId);

    /**
     * Returns a list of all active (PENDING or SUBMITTED/FILLED open) orders.
     */
    List<ExecutionOrder> getActiveOrders();
}
