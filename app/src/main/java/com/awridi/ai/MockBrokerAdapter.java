package com.awridi.ai;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MockBrokerAdapter
 * Mock / Demo Broker implementation for virtual paper trading.
 * Guaranteed zero real connection or financial API calls.
 */
public class MockBrokerAdapter implements BrokerAdapter {

    public static final String BROKER_NAME = "Mock/Demo Broker";
    private final List<ExecutionOrder> orders = new ArrayList<>();

    @Override
    public boolean isLiveTradingSupported() {
        return false; // Live trading strictly disabled
    }

    @Override
    public String getBrokerName() {
        return BROKER_NAME;
    }

    @Override
    public synchronized ExecutionOrder submitOrder(ExecutionOrder order) {
        if (order == null) {
            ExecutionOrder errOrder = new ExecutionOrder();
            errOrder.status = ExecutionOrder.OrderStatus.FAILED;
            errOrder.rejectionReason = "الأمر غير صالح (null order).";
            return errOrder;
        }

        // Safety Guard: Prohibit live trading execution unconditionally
        if (order.tradingMode == ExecutionOrder.TradingMode.LIVE_TRADING) {
            order.status = ExecutionOrder.OrderStatus.REJECTED;
            order.rejectionReason = "التداول الحقيقي غير مفعل — LIVE TRADING IS DISABLED";
            orders.add(order);
            return order;
        }

        order.brokerName = BROKER_NAME;

        if (order.orderType == ExecutionOrder.OrderType.MARKET) {
            order.status = ExecutionOrder.OrderStatus.FILLED;
            order.fillPrice = order.price > 0 ? order.price : 2650.0;
            order.filledTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            order.notes = "تم تنفيذ الأمر بالسعر الحالي للسوق (Market Order Filled).";
        } else if (order.orderType == ExecutionOrder.OrderType.LIMIT || order.orderType == ExecutionOrder.OrderType.STOP) {
            order.status = ExecutionOrder.OrderStatus.PENDING;
            order.notes = "الأمر قيد الانتظار لوصول السعر إلى القيمة المحددة (Pending Order).";
        } else {
            order.status = ExecutionOrder.OrderStatus.REJECTED;
            order.rejectionReason = "نوع الأمر غير معروف (Unknown order type).";
        }

        orders.add(order);
        return order;
    }

    @Override
    public synchronized ExecutionOrder cancelOrder(String orderId) {
        ExecutionOrder target = findOrderById(orderId);
        if (target == null) {
            ExecutionOrder err = new ExecutionOrder();
            err.status = ExecutionOrder.OrderStatus.FAILED;
            err.rejectionReason = "لم يتم العثور على الأمر المطلوب إلغاؤه ID: " + orderId;
            return err;
        }

        if (target.status == ExecutionOrder.OrderStatus.PENDING || target.status == ExecutionOrder.OrderStatus.SUBMITTED) {
            target.status = ExecutionOrder.OrderStatus.CANCELLED;
            target.notes = "تم إلغاء الأمر المعلق بنجاح.";
        } else if (target.status == ExecutionOrder.OrderStatus.CANCELLED) {
            target.rejectionReason = "الأمر ملغى بالفعل.";
        } else {
            target.rejectionReason = "لا يمكن إلغاء أمر بحالة " + target.status;
        }

        return target;
    }

    @Override
    public synchronized ExecutionOrder modifyOrder(String orderId, double newPrice, double newStopLoss, double newTakeProfit) {
        ExecutionOrder target = findOrderById(orderId);
        if (target == null) {
            ExecutionOrder err = new ExecutionOrder();
            err.status = ExecutionOrder.OrderStatus.FAILED;
            err.rejectionReason = "لم يتم العثور على الأمر المطلوب تعديله ID: " + orderId;
            return err;
        }

        if (target.status == ExecutionOrder.OrderStatus.PENDING || target.status == ExecutionOrder.OrderStatus.SUBMITTED) {
            if (newPrice > 0) target.price = newPrice;
            if (newStopLoss > 0) target.stopLoss = newStopLoss;
            if (newTakeProfit > 0) target.takeProfit = newTakeProfit;
            target.notes = "تم تعديل تفاصيل الأمر المعلق بنجاح.";
        } else if (target.status == ExecutionOrder.OrderStatus.FILLED) {
            if (newStopLoss > 0) target.stopLoss = newStopLoss;
            if (newTakeProfit > 0) target.takeProfit = newTakeProfit;
            target.notes = "تم تعديل مستويات SL / TP للصفقة المفتوحة.";
        } else {
            target.rejectionReason = "لا يمكن تعديل أمر ملغى أو مرفوض أو مغلق.";
        }

        return target;
    }

    @Override
    public synchronized ExecutionOrder queryOrderStatus(String orderId) {
        ExecutionOrder target = findOrderById(orderId);
        if (target == null) {
            ExecutionOrder err = new ExecutionOrder();
            err.status = ExecutionOrder.OrderStatus.FAILED;
            err.rejectionReason = "لم يتم العثور على الأمر ID: " + orderId;
            return err;
        }
        return target;
    }

    @Override
    public synchronized List<ExecutionOrder> getActiveOrders() {
        List<ExecutionOrder> active = new ArrayList<>();
        for (ExecutionOrder o : orders) {
            if (o.status == ExecutionOrder.OrderStatus.PENDING || o.status == ExecutionOrder.OrderStatus.SUBMITTED || o.status == ExecutionOrder.OrderStatus.FILLED) {
                active.add(o);
            }
        }
        return active;
    }

    /**
     * Evaluates pending orders against live currentPrice and fills them if triggered.
     */
    public synchronized List<ExecutionOrder> evaluateMarketTick(double currentPrice) {
        List<ExecutionOrder> updatedOrders = new ArrayList<>();
        String nowStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        for (ExecutionOrder o : orders) {
            if (o.status == ExecutionOrder.OrderStatus.PENDING || o.status == ExecutionOrder.OrderStatus.SUBMITTED) {
                boolean fill = false;
                if (o.orderType == ExecutionOrder.OrderType.LIMIT) {
                    if (o.action == ExecutionOrder.Action.BUY && currentPrice <= o.price) fill = true;
                    else if (o.action == ExecutionOrder.Action.SELL && currentPrice >= o.price) fill = true;
                } else if (o.orderType == ExecutionOrder.OrderType.STOP) {
                    if (o.action == ExecutionOrder.Action.BUY && currentPrice >= o.price) fill = true;
                    else if (o.action == ExecutionOrder.Action.SELL && currentPrice <= o.price) fill = true;
                }

                if (fill) {
                    o.status = ExecutionOrder.OrderStatus.FILLED;
                    o.fillPrice = currentPrice;
                    o.filledTimestamp = nowStr;
                    o.notes = "تم تنفيذ الأمر المعلق تلقائياً عند وصول السعر للهدف (" + currentPrice + ").";
                    updatedOrders.add(o);
                }
            }
        }
        return updatedOrders;
    }

    private ExecutionOrder findOrderById(String orderId) {
        if (orderId == null) return null;
        for (ExecutionOrder o : orders) {
            if (orderId.equals(o.orderId)) {
                return o;
            }
        }
        return null;
    }
}
