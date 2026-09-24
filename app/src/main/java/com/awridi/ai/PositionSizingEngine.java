package com.awridi.ai;

import java.util.Locale;

/**
 * PositionSizingEngine
 * Calculates position size for paper trading while strictly validating limits and exposure.
 */
public class PositionSizingEngine {

    public static final double DEFAULT_MAX_RISK_PCT = 2.0;
    public static final double DEFAULT_MAX_EXPOSURE_LOTS = 10.0;
    public static final int DEFAULT_MAX_OPEN_TRADES = 5;
    public static final double GOLD_LOT_MULTIPLIER = 100.0; // 1 Lot XAU/USD = $100 per $1 price movement

    public static class SizingResult {
        public boolean valid = false;
        public double positionSizeLots = 0.0;
        public double riskAmountUsd = 0.0;
        public double riskPercentage = 0.0;
        public double riskDistance = 0.0;
        public String rejectionReason = "";

        @Override
        public String toString() {
            return "SizingResult{" +
                    "valid=" + valid +
                    ", lots=" + String.format(Locale.US, "%.2f", positionSizeLots) +
                    ", riskUsd=" + String.format(Locale.US, "%.2f", riskAmountUsd) +
                    ", reason='" + rejectionReason + '\'' +
                    '}';
        }
    }

    public static SizingResult calculatePositionSize(
            double accountBalance,
            double riskPercentage,
            double entryPrice,
            double stopLoss,
            TradePosition.Direction direction,
            double currentOpenExposureLots,
            int currentOpenTradesCount) {
        return calculatePositionSize(accountBalance, riskPercentage, entryPrice, stopLoss, direction,
                currentOpenExposureLots, currentOpenTradesCount, DEFAULT_MAX_RISK_PCT, DEFAULT_MAX_EXPOSURE_LOTS, DEFAULT_MAX_OPEN_TRADES);
    }

    public static SizingResult calculatePositionSize(
            double accountBalance,
            double riskPercentage,
            double entryPrice,
            double stopLoss,
            TradePosition.Direction direction,
            double currentOpenExposureLots,
            int currentOpenTradesCount,
            double maxRiskPercentage,
            double maxExposureLots,
            int maxOpenTrades) {

        SizingResult res = new SizingResult();

        // 1. Boundary & NaN / Infinity checks
        if (!isValidNumber(accountBalance) || accountBalance <= 0) {
            res.valid = false;
            res.rejectionReason = "رأس المال غير صالح أو يساوي الصفر (Account Balance <= 0).";
            return res;
        }

        if (!isValidNumber(riskPercentage) || riskPercentage <= 0) {
            res.valid = false;
            res.rejectionReason = "نسبة المخاطرة غير صالحة أو تساوي الصفر (Risk Percentage <= 0).";
            return res;
        }

        if (maxRiskPercentage <= 0) {
            res.valid = false;
            res.rejectionReason = "حد المخاطرة الأقصى المسموح به يجب أن يكون أكبر من الصفر (Max Risk % <= 0).";
            return res;
        }

        double allowedMaxRisk = maxRiskPercentage;
        if (riskPercentage > allowedMaxRisk) {
            res.valid = false;
            res.rejectionReason = String.format(Locale.US,
                    "نسبة المخاطرة المطلوب (%.2f%%) تتجاوز الحد الأقصى المسموح به (%.2f%%).",
                    riskPercentage, allowedMaxRisk);
            return res;
        }

        if (!isValidNumber(entryPrice) || entryPrice <= 0) {
            res.valid = false;
            res.rejectionReason = "سعر الدخول غير صالح (Entry Price <= 0 / Invalid).";
            return res;
        }

        if (!isValidNumber(stopLoss) || stopLoss <= 0) {
            res.valid = false;
            res.rejectionReason = "وقف الخسارة غير صالح (Stop Loss <= 0 / Invalid).";
            return res;
        }

        // 2. Max Open Trades Protection
        if (maxOpenTrades <= 0) {
            res.valid = false;
            res.rejectionReason = "حد عدد الصفقات المفتوحة الأقصى يجب أن يكون أكبر من الصفر (Max Open Trades <= 0).";
            return res;
        }

        int allowedMaxOpen = maxOpenTrades;
        if (currentOpenTradesCount >= allowedMaxOpen) {
            res.valid = false;
            res.rejectionReason = String.format(Locale.US,
                    "تم الوصول للحد الأقصى لعدد الصفقات المفتوحة (%d من %d).",
                    currentOpenTradesCount, allowedMaxOpen);
            return res;
        }

        // 3. Direction & Risk Distance Calculation
        double riskDistance = 0.0;
        if (direction == TradePosition.Direction.BUY) {
            if (stopLoss >= entryPrice) {
                res.valid = false;
                res.rejectionReason = "وقف الخسارة للشراء يجب أن يكون أقل من سعر الدخول (BUY SL < Entry).";
                return res;
            }
            riskDistance = entryPrice - stopLoss;
        } else if (direction == TradePosition.Direction.SELL) {
            if (stopLoss <= entryPrice) {
                res.valid = false;
                res.rejectionReason = "وقف الخسارة للبيع يجب أن يكون أعلى من سعر الدخول (SELL SL > Entry).";
                return res;
            }
            riskDistance = stopLoss - entryPrice;
        } else {
            res.valid = false;
            res.rejectionReason = "اتجاه الصفقة غير محدد (Direction is NONE).";
            return res;
        }

        if (!isValidNumber(riskDistance) || riskDistance <= 0.0001) {
            res.valid = false;
            res.rejectionReason = "مسافة وقف الخسارة صغيرة جدًا أو غير صالحة.";
            return res;
        }

        // 4. Position Size Calculation
        double riskAmountUsd = accountBalance * (riskPercentage / 100.0);
        double calculatedLots = riskAmountUsd / (riskDistance * GOLD_LOT_MULTIPLIER);

        if (!isValidNumber(calculatedLots) || calculatedLots <= 0) {
            res.valid = false;
            res.rejectionReason = "حجم اللوت المحسوب غير صالح (Position size <= 0 / NaN / Infinity).";
            return res;
        }

        // 5. Exposure Limits Check
        if (maxExposureLots <= 0) {
            res.valid = false;
            res.rejectionReason = "حد التعرض الكلي الأقصى باللوت يجب أن يكون أكبر من الصفر (Max Exposure Lots <= 0).";
            return res;
        }

        double allowedMaxExposure = maxExposureLots;
        if (currentOpenExposureLots + calculatedLots > allowedMaxExposure + 0.00001) {
            res.valid = false;
            res.rejectionReason = String.format(Locale.US,
                    "حجم اللوت المطلوب (%.2f) يتجاوز الحد الأقصى المسموح به للتعرض الكلي (%.2f لوت).",
                    calculatedLots, allowedMaxExposure);
            return res;
        }

        res.valid = true;
        res.positionSizeLots = calculatedLots;
        res.riskAmountUsd = riskAmountUsd;
        res.riskPercentage = riskPercentage;
        res.riskDistance = riskDistance;
        return res;
    }

    private static boolean isValidNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
