package com.awridi.ai;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TradeMonitorEngine
 * Monitors open paper trade positions on a per-bar/per-price tick basis without Look-Ahead Bias.
 * Enforces the mandatory Conservative TP/SL Conflict Policy (CONSERVATIVE_SL_FIRST).
 */
public class TradeMonitorEngine {

    public static class MonitoringResult {
        public TradePosition position;
        public boolean statusChanged = false;
        public boolean conflictOccurred = false;
        public String conflictResolution = "";
        public String auditLogMessage = "";

        @Override
        public String toString() {
            return "MonitoringResult{" +
                    "status=" + (position != null ? position.status : "NULL") +
                    ", changed=" + statusChanged +
                    ", conflict=" + conflictOccurred +
                    ", res='" + conflictResolution + '\'' +
                    '}';
        }
    }

    /**
     * Evaluate position against single price tick.
     */
    public MonitoringResult monitorTick(TradePosition pos, double tickPrice, double accountBalance) {
        return monitorBar(pos, tickPrice, tickPrice, tickPrice, tickPrice, "", false, false, accountBalance);
    }

    /**
     * Evaluate position against single candle bar without intrabar ordering data.
     */
    public MonitoringResult monitorBar(TradePosition pos, MarketIntelligenceEngine.Bar bar, double accountBalance) {
        return monitorBar(pos, bar.open, bar.high, bar.low, bar.close, "", false, false, accountBalance);
    }

    /**
     * Evaluate position against single candle bar with optional intrabar sequence parameters.
     */
    public MonitoringResult monitorBar(
            TradePosition pos,
            double open,
            double high,
            double low,
            double close,
            String barTime,
            boolean hasIntrabarSequence,
            boolean isTpFirst,
            double accountBalance) {

        MonitoringResult res = new MonitoringResult();
        if (pos == null) return res;
        res.position = pos;

        if (pos.status != TradePosition.Status.OPEN && pos.status != TradePosition.Status.PENDING) {
            return res; // Already closed/resolved
        }

        // If PENDING, fill order at open price
        if (pos.status == TradePosition.Status.PENDING) {
            pos.status = TradePosition.Status.OPEN;
            pos.entryPrice = (pos.entryPrice > 0) ? pos.entryPrice : open;
            res.statusChanged = true;
        }

        pos.currentPrice = close;
        if (barTime != null && !barTime.isEmpty()) {
            pos.candleTime = barTime;
        }

        // Update MAE & MFE
        PnLEngine.updateExcursionMetrics(pos, high, low);

        // Update Unrealized PnL & R Multiple
        PnLEngine.PnLResult pnlRes = PnLEngine.evaluatePositionPnL(pos, close, accountBalance);
        pos.unrealizedPnL = pnlRes.pnlUsd;
        pos.rMultiple = pnlRes.rMultiple;

        boolean tpTouched = false;
        boolean slTouched = false;

        if (pos.direction == TradePosition.Direction.BUY) {
            tpTouched = high >= pos.takeProfit;
            slTouched = low <= pos.stopLoss;
        } else if (pos.direction == TradePosition.Direction.SELL) {
            tpTouched = low <= pos.takeProfit;
            slTouched = high >= pos.stopLoss;
        }

        String currentTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        // 1. CONFLICING TP & SL IN SAME CANDLE
        if (tpTouched && slTouched) {
            res.conflictOccurred = true;
            pos.conflictType = "TP_SL_CONFLICT";

            if (hasIntrabarSequence) {
                // Real Intrabar Sequence Available
                if (isTpFirst) {
                    pos.status = TradePosition.Status.TP_HIT;
                    pos.resolution = "REAL_INTRABAR_TP_FIRST";
                    pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, pos.takeProfit, pos.positionSize);
                    pos.currentPrice = pos.takeProfit;
                } else {
                    pos.status = TradePosition.Status.SL_HIT;
                    pos.resolution = "REAL_INTRABAR_SL_FIRST";
                    pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, pos.stopLoss, pos.positionSize);
                    pos.currentPrice = pos.stopLoss;
                }
            } else {
                // NO INTRABAR DATA -> MANDATORY CONSERVATIVE POLICY (CONSERVATIVE_SL_FIRST)
                pos.status = TradePosition.Status.SL_HIT;
                pos.resolution = "CONSERVATIVE_SL_FIRST";
                pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, pos.stopLoss, pos.positionSize);
                pos.currentPrice = pos.stopLoss;
            }

            pos.unrealizedPnL = 0.0;
            pos.rMultiple = PnLEngine.calculateRMultiple(pos.realizedPnL, pos.riskAmount);
            pos.closeTime = currentTimeStr;
            res.statusChanged = true;

            res.conflictResolution = pos.resolution;
            res.auditLogMessage = String.format(Locale.US,
                    "[TP_SL_CONFLICT] Trade ID: %s | Bar Time: %s | Dir: %s | Entry: %.2f | SL: %.2f | TP: %.2f | High: %.2f | Low: %.2f | ConflictType: %s | Resolution: %s | Status: %s | PnL: $%.2f",
                    pos.tradeId, pos.candleTime, pos.direction.name(), pos.entryPrice, pos.stopLoss, pos.takeProfit, high, low, pos.conflictType, pos.resolution, pos.status.name(), pos.realizedPnL);
            return res;
        }

        // 2. ONLY TP TOUCHED
        if (tpTouched) {
            pos.status = TradePosition.Status.TP_HIT;
            pos.currentPrice = pos.takeProfit;
            pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, pos.takeProfit, pos.positionSize);
            pos.unrealizedPnL = 0.0;
            pos.rMultiple = PnLEngine.calculateRMultiple(pos.realizedPnL, pos.riskAmount);
            pos.closeTime = currentTimeStr;
            res.statusChanged = true;
            res.auditLogMessage = String.format(Locale.US,
                    "[TP_HIT] Trade ID: %s | Entry: %.2f | TP: %.2f | Exit Price: %.2f | PnL: $%.2f | R: %.2f",
                    pos.tradeId, pos.entryPrice, pos.takeProfit, pos.takeProfit, pos.realizedPnL, pos.rMultiple);
            return res;
        }

        // 3. ONLY SL TOUCHED
        if (slTouched) {
            pos.status = TradePosition.Status.SL_HIT;
            pos.currentPrice = pos.stopLoss;
            pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, pos.stopLoss, pos.positionSize);
            pos.unrealizedPnL = 0.0;
            pos.rMultiple = PnLEngine.calculateRMultiple(pos.realizedPnL, pos.riskAmount);
            pos.closeTime = currentTimeStr;
            res.statusChanged = true;
            res.auditLogMessage = String.format(Locale.US,
                    "[SL_HIT] Trade ID: %s | Entry: %.2f | SL: %.2f | Exit Price: %.2f | PnL: $%.2f | R: %.2f",
                    pos.tradeId, pos.entryPrice, pos.stopLoss, pos.stopLoss, pos.realizedPnL, pos.rMultiple);
            return res;
        }

        return res;
    }
}
