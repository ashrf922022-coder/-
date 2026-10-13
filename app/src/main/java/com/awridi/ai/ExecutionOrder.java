package com.awridi.ai;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class ExecutionOrder {

    public enum Action {
        BUY,
        SELL
    }

    public enum OrderType {
        MARKET,
        LIMIT,
        STOP
    }

    public enum OrderStatus {
        PENDING,
        SUBMITTED,
        FILLED,
        PARTIALLY_FILLED,
        CANCELLED,
        REJECTED,
        FAILED
    }

    public enum TradingMode {
        PAPER_TRADING,
        LIVE_TRADING
    }

    public String orderId;
    public String symbol = MainActivity.GOLD_SYMBOL;
    public Action action = Action.BUY;
    public OrderType orderType = OrderType.MARKET;
    public OrderStatus status = OrderStatus.PENDING;
    public TradingMode tradingMode = TradingMode.PAPER_TRADING;

    public double price = 0.0;          // Target / Entry price
    public double fillPrice = 0.0;      // Actual fill price
    public double exitPrice = 0.0;      // Closed price
    public double stopLoss = 0.0;
    public double takeProfit = 0.0;
    public double lotSize = 0.1;
    public double riskAmount = 0.0;
    public double expectedProfit = 0.0;
    public double pnl = 0.0;
    public double riskRewardRatio = 0.0;

    public String createdTimestamp;
    public String filledTimestamp;
    public String closedTimestamp;

    public String rejectionReason = "";
    public String notes = "";
    public String brokerName = "Mock/Demo Broker";
    public String signalSource = "ExecutionEngine";

    public ExecutionOrder() {
        this.orderId = UUID.randomUUID().toString();
        this.createdTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    public ExecutionOrder(Action action, OrderType orderType, double price, double stopLoss, double takeProfit, double lotSize) {
        this();
        this.action = action;
        this.orderType = orderType;
        this.price = price;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.lotSize = lotSize;
    }

    @Override
    public String toString() {
        return "ExecutionOrder{" +
                "id='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", action=" + action +
                ", type=" + orderType +
                ", status=" + status +
                ", mode=" + tradingMode +
                ", price=" + String.format(Locale.US, "%.2f", price) +
                ", fillPrice=" + String.format(Locale.US, "%.2f", fillPrice) +
                ", SL=" + String.format(Locale.US, "%.2f", stopLoss) +
                ", TP=" + String.format(Locale.US, "%.2f", takeProfit) +
                ", lotSize=" + String.format(Locale.US, "%.2f", lotSize) +
                ", pnl=" + String.format(Locale.US, "%.2f", pnl) +
                ", reason='" + rejectionReason + '\'' +
                '}';
    }
}
