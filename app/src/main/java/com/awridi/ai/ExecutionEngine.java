package com.awridi.ai;

import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ExecutionEngine
 * Central Execution Engine responsible for managing, validating, and executing orders safely.
 * Strictly enforces pipeline:
 * Market Data -> Signal Engine -> Trade Setup -> Risk Management -> Execution Engine -> Mock Broker -> Portfolio / Paper Trading -> Order History
 */
public class ExecutionEngine {

    public static final boolean LIVE_TRADING_ENABLED = false; // Strictly disabled in this phase

    private BrokerAdapter brokerAdapter;
    private final RiskManagementEngine riskEngine;

    public ExecutionEngine() {
        this.brokerAdapter = new MockBrokerAdapter();
        this.riskEngine = new RiskManagementEngine();
    }

    public ExecutionEngine(BrokerAdapter brokerAdapter, RiskManagementEngine riskEngine) {
        this.brokerAdapter = brokerAdapter != null ? brokerAdapter : new MockBrokerAdapter();
        this.riskEngine = riskEngine != null ? riskEngine : new RiskManagementEngine();
    }

    public BrokerAdapter getBrokerAdapter() {
        return brokerAdapter;
    }

    public void setBrokerAdapter(BrokerAdapter brokerAdapter) {
        if (brokerAdapter != null) {
            this.brokerAdapter = brokerAdapter;
        }
    }

    public RiskManagementEngine getRiskEngine() {
        return riskEngine;
    }

    /**
     * Executes an order through the mandatory security and risk pipeline.
     */
    public ExecutionOrder executeOrder(ExecutionOrder order, double accountBalance, double currentDailyLoss, SharedPreferences prefs) {
        if (order == null) {
            ExecutionOrder err = new ExecutionOrder();
            err.status = ExecutionOrder.OrderStatus.FAILED;
            err.rejectionReason = "الأمر غير صالح (null order).";
            return err;
        }

        // 1. Live Trading Guardrail
        if (LIVE_TRADING_ENABLED || order.tradingMode == ExecutionOrder.TradingMode.LIVE_TRADING) {
            order.status = ExecutionOrder.OrderStatus.REJECTED;
            order.rejectionReason = "التداول الحقيقي غير مفعل — LIVE TRADING IS DISABLED";
            logOrderToHistory(prefs, order);
            return order;
        }

        // 2. Kill Switch Security Check
        if (KillSwitch.isActive(prefs)) {
            order.status = ExecutionOrder.OrderStatus.REJECTED;
            order.rejectionReason = "تم رفض الأمر: نظام أمان الطوارئ (Kill Switch) مفعل حالياً.";
            logOrderToHistory(prefs, order);
            return order;
        }

        // 3. Risk Management Mandatory Validation
        TradeSetup.Direction dir = order.action == ExecutionOrder.Action.BUY ? TradeSetup.Direction.BUY : TradeSetup.Direction.SELL;

        double riskPct = 1.0;
        if (prefs != null) {
            try {
                riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
            } catch (Exception ignored) {}
        }

        RiskManagementEngine.RiskResult riskRes = riskEngine.evaluateRisk(
                accountBalance,
                riskPct,
                order.price,
                order.stopLoss,
                order.takeProfit,
                dir,
                currentDailyLoss
        );

        if (!riskRes.valid) {
            order.status = ExecutionOrder.OrderStatus.REJECTED;
            order.rejectionReason = "تم رفض الأمر بواسطة إدارة المخاطر: " + riskRes.rejectionReason;
            logOrderToHistory(prefs, order);
            return order;
        }

        // Apply calculated risk parameters to order
        order.riskAmount = riskRes.riskAmount;
        order.riskRewardRatio = riskRes.riskRewardRatio;
        order.expectedProfit = riskRes.riskAmount * riskRes.riskRewardRatio;
        if (order.lotSize <= 0) {
            order.lotSize = riskRes.positionSize;
        }

        // 4. Submit Order to Broker Adapter (Mock/Demo Broker)
        ExecutionOrder processedOrder = brokerAdapter.submitOrder(order);

        // 5. Update Portfolio & Paper Trading if Filled
        if (processedOrder.status == ExecutionOrder.OrderStatus.FILLED && prefs != null) {
            syncWithPortfolio(prefs, processedOrder);
        }

        // 6. Record in Order History
        logOrderToHistory(prefs, processedOrder);

        return processedOrder;
    }

    /**
     * Creates and executes an order directly from a TradeSetup object.
     */
    public ExecutionOrder executeFromTradeSetup(TradeSetup setup, ExecutionOrder.OrderType orderType, double accountBalance, double currentDailyLoss, SharedPreferences prefs) {
        if (setup == null || !setup.valid) {
            ExecutionOrder err = new ExecutionOrder();
            err.status = ExecutionOrder.OrderStatus.REJECTED;
            err.rejectionReason = setup == null ? "إعداد الصفقة غير متوفر (null setup)." : "إعداد الصفقة غير صالح (TradeSetup invalid).";
            logOrderToHistory(prefs, err);
            return err;
        }

        ExecutionOrder order = new ExecutionOrder();
        order.action = setup.direction == TradeSetup.Direction.BUY ? ExecutionOrder.Action.BUY : ExecutionOrder.Action.SELL;
        order.orderType = orderType != null ? orderType : ExecutionOrder.OrderType.MARKET;
        order.price = setup.entryPrice;
        order.stopLoss = setup.stopLoss;
        order.takeProfit = setup.takeProfit;
        order.signalSource = "TradeSetup Engine";

        return executeOrder(order, accountBalance, currentDailyLoss, prefs);
    }

    /**
     * Cancels an active or pending order.
     */
    public ExecutionOrder cancelOrder(String orderId, SharedPreferences prefs) {
        ExecutionOrder cancelled = brokerAdapter.cancelOrder(orderId);
        if (cancelled != null && prefs != null) {
            logOrderToHistory(prefs, cancelled);
        }
        return cancelled;
    }

    /**
     * Modifies an active or pending order.
     */
    public ExecutionOrder modifyOrder(String orderId, double newPrice, double newStopLoss, double newTakeProfit, SharedPreferences prefs) {
        ExecutionOrder modified = brokerAdapter.modifyOrder(orderId, newPrice, newStopLoss, newTakeProfit);
        if (modified != null && prefs != null) {
            logOrderToHistory(prefs, modified);
        }
        return modified;
    }

    /**
     * Process market tick to trigger pending orders or check SL/TP on active positions.
     */
    public void processMarketTick(double currentPrice, SharedPreferences prefs) {
        if (brokerAdapter instanceof MockBrokerAdapter) {
            List<ExecutionOrder> triggered = ((MockBrokerAdapter) brokerAdapter).evaluateMarketTick(currentPrice);
            for (ExecutionOrder o : triggered) {
                if (prefs != null) {
                    syncWithPortfolio(prefs, o);
                    logOrderToHistory(prefs, o);
                }
            }
        }
    }

    private void syncWithPortfolio(SharedPreferences prefs, ExecutionOrder order) {
        try {
            AIDecisionResult aiResult = new AIDecisionResult();
            aiResult.decision = order.action == ExecutionOrder.Action.BUY ? AIDecisionResult.Decision.BUY : AIDecisionResult.Decision.SELL;
            aiResult.entryPrice = order.fillPrice > 0 ? order.fillPrice : order.price;
            aiResult.stopLoss = order.stopLoss;
            aiResult.takeProfit = order.takeProfit;
            aiResult.positionSizeLot = order.lotSize;
            aiResult.riskRewardRatio = order.riskRewardRatio;
            aiResult.riskApproved = true;

            PaperTradeEngine pEngine = new PaperTradeEngine();
            pEngine.openPaperTradeFromAIDecision(aiResult, prefs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- ORDER HISTORY PERSISTENCE ---
    public static final String PREF_KEY_ORDER_HISTORY = "order_history_json";

    public static List<ExecutionOrder> loadOrderHistory(SharedPreferences prefs) {
        List<ExecutionOrder> list = new ArrayList<>();
        if (prefs == null) return list;
        try {
            String jsonStr = prefs.getString(PREF_KEY_ORDER_HISTORY, "[]");
            if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
                return list;
            }

            // Fallback lightweight regex JSON parser
            org.json.JSONArray arr = new org.json.JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                ExecutionOrder o = new ExecutionOrder();
                o.orderId = obj.optString("orderId", o.orderId);
                o.symbol = obj.optString("symbol", MainActivity.GOLD_SYMBOL);
                o.action = ExecutionOrder.Action.valueOf(obj.optString("action", "BUY"));
                o.orderType = ExecutionOrder.OrderType.valueOf(obj.optString("orderType", "MARKET"));
                o.status = ExecutionOrder.OrderStatus.valueOf(obj.optString("status", "PENDING"));
                o.tradingMode = ExecutionOrder.TradingMode.valueOf(obj.optString("tradingMode", "PAPER_TRADING"));
                o.price = obj.optDouble("price", 0.0);
                o.fillPrice = obj.optDouble("fillPrice", 0.0);
                o.exitPrice = obj.optDouble("exitPrice", 0.0);
                o.stopLoss = obj.optDouble("stopLoss", 0.0);
                o.takeProfit = obj.optDouble("takeProfit", 0.0);
                o.lotSize = obj.optDouble("lotSize", 0.1);
                o.riskAmount = obj.optDouble("riskAmount", 0.0);
                o.pnl = obj.optDouble("pnl", 0.0);
                o.createdTimestamp = obj.optString("createdTimestamp", "");
                o.filledTimestamp = obj.optString("filledTimestamp", "");
                o.rejectionReason = obj.optString("rejectionReason", "");
                o.notes = obj.optString("notes", "");
                o.brokerName = obj.optString("brokerName", "Mock/Demo Broker");
                o.signalSource = obj.optString("signalSource", "ExecutionEngine");
                list.add(o);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveOrderHistory(SharedPreferences prefs, List<ExecutionOrder> list) {
        if (prefs == null || list == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                ExecutionOrder o = list.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"orderId\":\"").append(o.orderId != null ? o.orderId : "").append("\",")
                  .append("\"symbol\":\"").append(o.symbol != null ? o.symbol : "").append("\",")
                  .append("\"action\":\"").append(o.action.name()).append("\",")
                  .append("\"orderType\":\"").append(o.orderType.name()).append("\",")
                  .append("\"status\":\"").append(o.status.name()).append("\",")
                  .append("\"tradingMode\":\"").append(o.tradingMode.name()).append("\",")
                  .append("\"price\":").append(String.format(Locale.US, "%.2f", o.price)).append(",")
                  .append("\"fillPrice\":").append(String.format(Locale.US, "%.2f", o.fillPrice)).append(",")
                  .append("\"exitPrice\":").append(String.format(Locale.US, "%.2f", o.exitPrice)).append(",")
                  .append("\"stopLoss\":").append(String.format(Locale.US, "%.2f", o.stopLoss)).append(",")
                  .append("\"takeProfit\":").append(String.format(Locale.US, "%.2f", o.takeProfit)).append(",")
                  .append("\"lotSize\":").append(String.format(Locale.US, "%.2f", o.lotSize)).append(",")
                  .append("\"riskAmount\":").append(String.format(Locale.US, "%.2f", o.riskAmount)).append(",")
                  .append("\"pnl\":").append(String.format(Locale.US, "%.2f", o.pnl)).append(",")
                  .append("\"createdTimestamp\":\"").append(o.createdTimestamp != null ? o.createdTimestamp : "").append("\",")
                  .append("\"filledTimestamp\":\"").append(o.filledTimestamp != null ? o.filledTimestamp : "").append("\",")
                  .append("\"rejectionReason\":\"").append(o.rejectionReason != null ? o.rejectionReason.replace("\"", "'") : "").append("\",")
                  .append("\"notes\":\"").append(o.notes != null ? o.notes.replace("\"", "'") : "").append("\",")
                  .append("\"brokerName\":\"").append(o.brokerName != null ? o.brokerName : "").append("\",")
                  .append("\"signalSource\":\"").append(o.signalSource != null ? o.signalSource : "").append("\"")
                  .append("}");
            }
            sb.append("]");
            prefs.edit().putString(PREF_KEY_ORDER_HISTORY, sb.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void logOrderToHistory(SharedPreferences prefs, ExecutionOrder order) {
        if (prefs == null || order == null) return;
        List<ExecutionOrder> history = loadOrderHistory(prefs);

        // Update if exists or add new
        boolean found = false;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).orderId.equals(order.orderId)) {
                history.set(i, order);
                found = true;
                break;
            }
        }
        if (!found) {
            history.add(order);
        }
        saveOrderHistory(prefs, history);
    }
}
