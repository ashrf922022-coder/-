package com.awridi.ai;

import java.util.Locale;

/**
 * PnLEngine
 * Calculates unrealized, realized P&L, R-Multiple, Return %, and Excursion metrics for Paper Trades.
 */
public class PnLEngine {

    public static final double GOLD_LOT_MULTIPLIER = 100.0;

    public static class PnLResult {
        public double pnlUsd = 0.0;
        public double returnPercentage = 0.0;
        public double rMultiple = 0.0;
        public boolean isWin = false;

        @Override
        public String toString() {
            return "PnLResult{" +
                    "pnlUsd=" + String.format(Locale.US, "%+.2f", pnlUsd) +
                    ", returnPct=" + String.format(Locale.US, "%+.2f%%", returnPercentage) +
                    ", R=" + String.format(Locale.US, "%+.2f", rMultiple) +
                    ", win=" + isWin +
                    '}';
        }
    }

    public static double calculatePnL(TradePosition.Direction direction, double entryPrice, double exitPrice, double positionSizeLots) {
        if (direction == TradePosition.Direction.BUY) {
            return (exitPrice - entryPrice) * positionSizeLots * GOLD_LOT_MULTIPLIER;
        } else if (direction == TradePosition.Direction.SELL) {
            return (entryPrice - exitPrice) * positionSizeLots * GOLD_LOT_MULTIPLIER;
        }
        return 0.0;
    }

    public static double calculateRMultiple(double pnlUsd, double riskAmountUsd) {
        if (riskAmountUsd <= 0.00001) return 0.0;
        return pnlUsd / riskAmountUsd;
    }

    public static double calculateRMultiple(TradePosition.Direction direction, double entryPrice, double exitPrice, double stopLoss) {
        double riskDist = Math.abs(entryPrice - stopLoss);
        if (riskDist <= 0.00001) return 0.0;

        if (direction == TradePosition.Direction.BUY) {
            return (exitPrice - entryPrice) / riskDist;
        } else if (direction == TradePosition.Direction.SELL) {
            return (entryPrice - exitPrice) / riskDist;
        }
        return 0.0;
    }

    public static PnLResult evaluatePositionPnL(TradePosition position, double currentPrice, double accountBalance) {
        PnLResult res = new PnLResult();
        if (position == null) return res;

        res.pnlUsd = calculatePnL(position.direction, position.entryPrice, currentPrice, position.positionSize);
        res.isWin = res.pnlUsd > 0;

        if (position.riskAmount > 0) {
            res.rMultiple = calculateRMultiple(res.pnlUsd, position.riskAmount);
        } else {
            res.rMultiple = calculateRMultiple(position.direction, position.entryPrice, currentPrice, position.stopLoss);
        }

        if (accountBalance > 0) {
            res.returnPercentage = (res.pnlUsd / accountBalance) * 100.0;
        }

        return res;
    }

    public static void updateExcursionMetrics(TradePosition position, double barHigh, double barLow) {
        if (position == null) return;

        if (position.direction == TradePosition.Direction.BUY) {
            double favMove = barHigh - position.entryPrice;
            double advMove = position.entryPrice - barLow;
            if (favMove > position.mfe) position.mfe = favMove;
            if (advMove > position.mae) position.mae = advMove;
        } else if (position.direction == TradePosition.Direction.SELL) {
            double favMove = position.entryPrice - barLow;
            double advMove = barHigh - position.entryPrice;
            if (favMove > position.mfe) position.mfe = favMove;
            if (advMove > position.mae) position.mae = advMove;
        }
    }
}
