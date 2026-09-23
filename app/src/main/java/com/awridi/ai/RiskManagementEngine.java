package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RiskManagementEngine {

    public static final double DEFAULT_RISK_PER_TRADE_PCT = 1.0;
    public static final double DEFAULT_MAX_RISK_PER_TRADE_PCT = 2.0;
    public static final double DEFAULT_MAX_DAILY_LOSS_PCT = 3.0;
    public static final double DEFAULT_MIN_RR_RATIO = 1.5;
    public static final double DEFAULT_MAX_POSITION_SIZE = 10.0;

    private double defaultRiskPercentage = DEFAULT_RISK_PER_TRADE_PCT;
    private double maxRiskPercentage = DEFAULT_MAX_RISK_PER_TRADE_PCT;
    private double maxDailyLossPercentage = DEFAULT_MAX_DAILY_LOSS_PCT;
    private double minRiskRewardRatio = DEFAULT_MIN_RR_RATIO;
    private double maxPositionSizeCap = DEFAULT_MAX_POSITION_SIZE;

    public RiskManagementEngine() {
    }

    public RiskManagementEngine(double defaultRiskPercentage, double maxRiskPercentage, double maxDailyLossPercentage, double minRiskRewardRatio) {
        setDefaultRiskPercentage(defaultRiskPercentage);
        setMaxRiskPercentage(maxRiskPercentage);
        setMaxDailyLossPercentage(maxDailyLossPercentage);
        setMinRiskRewardRatio(minRiskRewardRatio);
    }

    // Getters and Setters for configurable settings
    public double getDefaultRiskPercentage() {
        return defaultRiskPercentage;
    }

    public void setDefaultRiskPercentage(double defaultRiskPercentage) {
        if (isValidNumber(defaultRiskPercentage) && defaultRiskPercentage > 0) {
            this.defaultRiskPercentage = defaultRiskPercentage;
        }
    }

    public double getMaxRiskPercentage() {
        return maxRiskPercentage;
    }

    public void setMaxRiskPercentage(double maxRiskPercentage) {
        if (isValidNumber(maxRiskPercentage) && maxRiskPercentage > 0) {
            this.maxRiskPercentage = maxRiskPercentage;
        }
    }

    public double getMaxDailyLossPercentage() {
        return maxDailyLossPercentage;
    }

    public void setMaxDailyLossPercentage(double maxDailyLossPercentage) {
        if (isValidNumber(maxDailyLossPercentage) && maxDailyLossPercentage > 0) {
            this.maxDailyLossPercentage = maxDailyLossPercentage;
        }
    }

    public double getMinRiskRewardRatio() {
        return minRiskRewardRatio;
    }

    public void setMinRiskRewardRatio(double minRiskRewardRatio) {
        if (isValidNumber(minRiskRewardRatio) && minRiskRewardRatio > 0) {
            this.minRiskRewardRatio = minRiskRewardRatio;
        }
    }

    public double getMaxPositionSizeCap() {
        return maxPositionSizeCap;
    }

    public void setMaxPositionSizeCap(double maxPositionSizeCap) {
        if (isValidNumber(maxPositionSizeCap) && maxPositionSizeCap > 0) {
            this.maxPositionSizeCap = maxPositionSizeCap;
        }
    }

    public static class RiskResult {
        public double accountBalance = 0.0;
        public double riskPercentage = 0.0;
        public double riskAmount = 0.0;
        public double entryPrice = 0.0;
        public double stopLoss = 0.0;
        public double takeProfit = 0.0;
        public double riskDistance = 0.0;
        public double rewardDistance = 0.0;
        public double riskRewardRatio = 0.0;
        public double positionSize = 0.0;
        public double maximumPositionSize = 0.0;
        public double maximumDailyLoss = 0.0; // Max daily loss percentage or amount depending on context
        public double maximumDailyLossAmount = 0.0;
        public double currentDailyLoss = 0.0;
        public double remainingDailyRisk = 0.0;
        public boolean valid = false;
        public String rejectionReason = "";
        public List<String> warnings = new ArrayList<>();
        public TradeSetup.Direction direction = TradeSetup.Direction.NONE;

        @Override
        public String toString() {
            return "RiskResult{" +
                    "balance=" + String.format(Locale.US, "%.2f", accountBalance) +
                    ", riskPct=" + String.format(Locale.US, "%.2f%%", riskPercentage) +
                    ", riskAmount=" + String.format(Locale.US, "%.2f", riskAmount) +
                    ", entry=" + String.format(Locale.US, "%.2f", entryPrice) +
                    ", SL=" + String.format(Locale.US, "%.2f", stopLoss) +
                    ", TP=" + String.format(Locale.US, "%.2f", takeProfit) +
                    ", R:R=" + String.format(Locale.US, "%.2f", riskRewardRatio) +
                    ", lotSize=" + String.format(Locale.US, "%.2f", positionSize) +
                    ", valid=" + valid +
                    ", reason='" + rejectionReason + '\'' +
                    '}';
        }
    }

    private static boolean isValidNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /**
     * Evaluates risk parameters and calculates position size safely.
     */
    public RiskResult evaluateRisk(double accountBalance, double riskPercentage, double entryPrice, double stopLoss, double takeProfit, TradeSetup.Direction direction, double currentDailyLoss) {
        return evaluateRisk(accountBalance, riskPercentage, entryPrice, stopLoss, takeProfit, direction, currentDailyLoss, this.maxPositionSizeCap);
    }

    public RiskResult evaluateRisk(double accountBalance, double riskPercentage, double entryPrice, double stopLoss, double takeProfit, TradeSetup.Direction direction, double currentDailyLoss, double customMaxPositionSize) {
        RiskResult result = new RiskResult();
        result.direction = direction != null ? direction : TradeSetup.Direction.NONE;
        result.currentDailyLoss = isValidNumber(currentDailyLoss) ? Math.max(0.0, currentDailyLoss) : 0.0;
        result.maximumPositionSize = (isValidNumber(customMaxPositionSize) && customMaxPositionSize > 0) ? customMaxPositionSize : this.maxPositionSizeCap;
        result.maximumDailyLoss = this.maxDailyLossPercentage;

        // 1. Validate inputs for NaN, Infinity, boundaries
        if (!isValidNumber(accountBalance) || accountBalance <= 0) {
            result.valid = false;
            result.rejectionReason = "رأس المال غير صالح أو يساوي الصفر (Account balance <= 0).";
            return result;
        }
        result.accountBalance = accountBalance;

        if (!isValidNumber(riskPercentage) || riskPercentage <= 0) {
            result.valid = false;
            result.rejectionReason = "نسبة المخاطرة غير صالحة أو تساوي الصفر (Risk percentage <= 0).";
            return result;
        }

        if (riskPercentage > this.maxRiskPercentage) {
            result.valid = false;
            result.rejectionReason = String.format(Locale.US,
                    "نسبة المخاطرة المطلوبة (%.2f%%) تتجاوز الحد الأقصى المسموح به للمخاطرة (%.2f%%).",
                    riskPercentage, this.maxRiskPercentage);
            return result;
        }
        result.riskPercentage = riskPercentage;

        if (!isValidNumber(entryPrice) || entryPrice <= 0) {
            result.valid = false;
            result.rejectionReason = "سعر الدخول غير صالح (Entry price invalid).";
            return result;
        }
        result.entryPrice = entryPrice;

        if (!isValidNumber(stopLoss) || stopLoss <= 0) {
            result.valid = false;
            result.rejectionReason = "وقف الخسارة غير صالح (Stop loss invalid).";
            return result;
        }
        result.stopLoss = stopLoss;

        if (!isValidNumber(takeProfit) || takeProfit <= 0) {
            result.valid = false;
            result.rejectionReason = "هدف الربح غير صالح (Take profit invalid).";
            return result;
        }
        result.takeProfit = takeProfit;

        if (result.direction == TradeSetup.Direction.NONE) {
            result.valid = false;
            result.rejectionReason = "اتجاه الصفقة غير محدد (Direction is NONE).";
            return result;
        }

        // 2. Daily Loss Protection Check
        result.maximumDailyLossAmount = accountBalance * (this.maxDailyLossPercentage / 100.0);
        result.remainingDailyRisk = Math.max(0.0, result.maximumDailyLossAmount - result.currentDailyLoss);

        if (result.currentDailyLoss >= result.maximumDailyLossAmount) {
            result.valid = false;
            result.rejectionReason = "تم الوصول إلى الحد الأقصى للخسارة اليومية";
            return result;
        }

        // 3. Calculate Risk Amount
        result.riskAmount = accountBalance * (riskPercentage / 100.0);

        // Check if adding this riskAmount exceeds remaining daily loss allowance
        if (result.currentDailyLoss + result.riskAmount > result.maximumDailyLossAmount + 0.00001) {
            result.valid = false;
            result.rejectionReason = String.format(Locale.US,
                    "حجم المخاطرة للصفقة ($%.2f) يتجاوز المتبقي من الحد الأقصى للخسارة اليومية ($%.2f).",
                    result.riskAmount, result.remainingDailyRisk);
            return result;
        }

        // 4. Calculate Risk Distance & Reward Distance based on Direction
        if (result.direction == TradeSetup.Direction.BUY) {
            if (stopLoss >= entryPrice) {
                result.valid = false;
                result.rejectionReason = "وقف الخسارة يجب أن يكون أقل من سعر الدخول لصفقات الشراء (BUY SL < Entry).";
                return result;
            }
            if (takeProfit <= entryPrice) {
                result.valid = false;
                result.rejectionReason = "هدف الربح يجب أن يكون أعلى من سعر الدخول لصفقات الشراء (BUY TP > Entry).";
                return result;
            }
            result.riskDistance = entryPrice - stopLoss;
            result.rewardDistance = takeProfit - entryPrice;
        } else if (result.direction == TradeSetup.Direction.SELL) {
            if (stopLoss <= entryPrice) {
                result.valid = false;
                result.rejectionReason = "وقف الخسارة يجب أن يكون أعلى من سعر الدخول لصفقات البيع (SELL SL > Entry).";
                return result;
            }
            if (takeProfit >= entryPrice) {
                result.valid = false;
                result.rejectionReason = "هدف الربح يجب أن يكون أقل من سعر الدخول لصفقات البيع (SELL TP < Entry).";
                return result;
            }
            result.riskDistance = stopLoss - entryPrice;
            result.rewardDistance = entryPrice - takeProfit;
        }

        if (!isValidNumber(result.riskDistance) || result.riskDistance <= 0.0000001) {
            result.valid = false;
            result.rejectionReason = "مسافة وقف الخسارة غير صالحة أو صغيرة جدًا.";
            return result;
        }

        if (!isValidNumber(result.rewardDistance) || result.rewardDistance <= 0.0000001) {
            result.valid = false;
            result.rejectionReason = "مسافة أخذ الربح غير صالحة أو صغيرة جدًا.";
            return result;
        }

        // 5. Calculate Risk/Reward Ratio
        result.riskRewardRatio = result.rewardDistance / result.riskDistance;
        if (!isValidNumber(result.riskRewardRatio) || result.riskRewardRatio < this.minRiskRewardRatio) {
            result.valid = false;
            result.rejectionReason = String.format(Locale.US,
                    "نسبة المخاطرة إلى العائد (%.2f) أقل من الحد الأدنى المقبول (%.2f).",
                    result.riskRewardRatio, this.minRiskRewardRatio);
            return result;
        }

        // 6. Calculate Position Size (1 Standard Lot XAU/USD = $100 per $1 move)
        double calculatedPositionSize = result.riskAmount / (result.riskDistance * 100.0);
        if (!isValidNumber(calculatedPositionSize) || calculatedPositionSize <= 0) {
            result.valid = false;
            result.rejectionReason = "حجم الصفقة المحسوب غير صالح (Position size <= 0 / NaN).";
            return result;
        }

        // Cap Position Size if it exceeds maximumPositionSize
        if (calculatedPositionSize > result.maximumPositionSize) {
            result.positionSize = result.maximumPositionSize;
            result.warnings.add(String.format(Locale.US,
                    "تم تقليص حجم الصفقة من %.2f إلى الحد الأقصى المسموح به %.2f لوت.",
                    calculatedPositionSize, result.maximumPositionSize));
        } else {
            result.positionSize = calculatedPositionSize;
        }

        result.valid = true;
        return result;
    }

    /**
     * Integrates RiskManagementEngine directly with an existing TradeSetup object.
     */
    public RiskResult evaluateTradeSetupRisk(TradeSetup setup, double accountBalance, double riskPercentage, double currentDailyLoss) {
        if (setup == null || !setup.valid) {
            RiskResult res = new RiskResult();
            res.valid = false;
            res.rejectionReason = setup == null ? "إعداد الصفقة غير متوفر (null)." : "إعداد الصفقة غير صالح (TradeSetup invalid).";
            return res;
        }

        return evaluateRisk(
                accountBalance,
                riskPercentage,
                setup.entryPrice,
                setup.stopLoss,
                setup.takeProfit,
                setup.direction,
                currentDailyLoss,
                this.maxPositionSizeCap
        );
    }
}
