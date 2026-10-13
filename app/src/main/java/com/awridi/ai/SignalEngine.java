package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 6: Market Intelligence & Signal Engine
 * Responsible for market analysis, trend detection, momentum, volatility,
 * entry points, SL/TP calculation, risk/reward assessment, confidence score,
 * and producing structured BUY / SELL / HOLD trading signals without Look-Ahead Bias.
 */
public class SignalEngine {

    public enum SignalType {
        BUY,
        SELL,
        HOLD
    }

    public enum Trend {
        BULLISH,  // صاعد
        BEARISH,  // هابط
        SIDEWAYS  // جانبي
    }

    public static class SignalResult {
        public SignalType signalType = SignalType.HOLD;
        public Trend trend = Trend.SIDEWAYS;
        public String trendName = "جانبي (SIDEWAYS)";
        public String trendStrength = "ضعيفة (WEAK)";
        public String momentum = "محايد (NEUTRAL)";
        public String volatility = "متوسط (NORMAL)";

        public double entryPrice = 0.0;
        public double suggestedStopLoss = 0.0;
        public double suggestedTakeProfit = 0.0;
        public double suggestedRiskReward = 0.0;

        public double confidence = 0.0; // Confidence percentage (0.0 to 100.0)
        public String signalReason = "";
        public List<String> supportingFactors = new ArrayList<>();
        public List<String> conflictingFactors = new ArrayList<>();

        public boolean valid = false;
        public String rejectionReason = "";

        public TradingDecisionResult tradingDecisionResult;

        @Override
        public String toString() {
            return "SignalResult{" +
                    "signalType=" + signalType +
                    ", trend=" + trend +
                    ", entryPrice=" + String.format(Locale.US, "%.2f", entryPrice) +
                    ", SL=" + String.format(Locale.US, "%.2f", suggestedStopLoss) +
                    ", TP=" + String.format(Locale.US, "%.2f", suggestedTakeProfit) +
                    ", R:R=" + String.format(Locale.US, "%.2f", suggestedRiskReward) +
                    ", confidence=" + String.format(Locale.US, "%.1f%%", confidence) +
                    ", valid=" + valid +
                    ", reason='" + signalReason + '\'' +
                    '}';
        }
    }

    private double minRiskRewardRatio = TradeSetupEngine.DEFAULT_MIN_RR;
    private double minConfidenceThreshold = 60.0; // Minimum required confidence % for BUY/SELL

    public SignalEngine() {
    }

    public SignalEngine(double minRiskRewardRatio, double minConfidenceThreshold) {
        this.minRiskRewardRatio = minRiskRewardRatio > 0 ? minRiskRewardRatio : TradeSetupEngine.DEFAULT_MIN_RR;
        this.minConfidenceThreshold = minConfidenceThreshold >= 0 ? minConfidenceThreshold : 60.0;
    }

    public double getMinRiskRewardRatio() {
        return minRiskRewardRatio;
    }

    public void setMinRiskRewardRatio(double minRiskRewardRatio) {
        if (minRiskRewardRatio > 0) {
            this.minRiskRewardRatio = minRiskRewardRatio;
        }
    }

    public double getMinConfidenceThreshold() {
        return minConfidenceThreshold;
    }

    public void setMinConfidenceThreshold(double minConfidenceThreshold) {
        if (minConfidenceThreshold >= 0) {
            this.minConfidenceThreshold = minConfidenceThreshold;
        }
    }

    /**
     * Generates a trading signal evaluated at the latest available candle in the bars list.
     */
    public SignalResult generateSignal(List<MarketIntelligenceEngine.Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return createInvalidSignal("قائمة بيانات السوق فارغة أو غير متوفرة (null/empty).");
        }
        return generateSignalAtCandle(bars, bars.size() - 1);
    }

    /**
     * Generates a trading signal strictly evaluated up to `endIndex` to guarantee ZERO Look-Ahead Bias.
     */
    public SignalResult generateSignalAtCandle(List<MarketIntelligenceEngine.Bar> bars, int endIndex) {
        SignalResult result = new SignalResult();

        // 1. Validate inputs & sufficient data length
        if (bars == null || bars.isEmpty()) {
            return createInvalidSignal("بيانات السوق غير متوفرة (null/empty).");
        }

        if (endIndex < 0 || endIndex >= bars.size()) {
            return createInvalidSignal("مؤشر الشمعة المستهدف غير صالح أو خارج نطاق البيانات.");
        }

        if (endIndex < 29) { // Need at least 30 candles (index 0 to 29)
            return createInvalidSignal("عدد البيانات غير كافٍ للتحليل الإشاري. نحتاج إلى 30 شمعة على الأقل.");
        }

        MarketIntelligenceEngine.Bar targetBar = bars.get(endIndex);
        if (Double.isNaN(targetBar.close) || Double.isInfinite(targetBar.close) || targetBar.close <= 0) {
            return createInvalidSignal("سعر الشمعة الحالية غير صالح أو يحتوي على قيمة غير معرفة (NaN/Infinity).");
        }

        // 2. Evaluate decision via TradingDecisionEngine strictly up to `endIndex`
        TradingDecisionEngine decisionEngine = new TradingDecisionEngine();
        TradingDecisionResult decisionRes = decisionEngine.evaluateAtCandle(bars, endIndex);
        result.tradingDecisionResult = decisionRes;

        if (decisionRes == null) {
            return createInvalidSignal("فشل تقييم قرار التداول (TradingDecisionResult is null).");
        }

        result.entryPrice = decisionRes.currentPrice;
        result.confidence = decisionRes.confidence;

        if (decisionRes.supportingFactors != null) {
            result.supportingFactors.addAll(decisionRes.supportingFactors);
        }
        if (decisionRes.conflictingFactors != null) {
            result.conflictingFactors.addAll(decisionRes.conflictingFactors);
        }

        // 3. Map Trend Analysis (صاعد / هابط / جانبي)
        mapTrend(decisionRes, result);

        // 4. Map Momentum & Volatility
        result.momentum = decisionRes.momentum != null ? decisionRes.momentum : "محايد (NEUTRAL)";
        result.volatility = decisionRes.volatility != null ? decisionRes.volatility : "متوسط (NORMAL)";

        // 5. Determine Entry, SL, TP, and Risk/Reward
        double atr = decisionRes.atrValue;
        if (Double.isNaN(atr) || Double.isInfinite(atr) || atr <= 0) {
            atr = 1.0; // safe fallback for ATR
        }

        double support = decisionRes.support;
        double resistance = decisionRes.resistance;

        if (decisionRes.decision == TradingDecisionResult.Decision.BUY) {
            result.signalType = SignalType.BUY;

            // Calculate SL and TP for BUY
            double atrMultiplierSL = 1.5;
            double atrMultiplierTP = 2.25;

            if (decisionRes.marketRegime != null && decisionRes.marketRegime.regime == MarketRegimeResult.Regime.HIGH_VOLATILITY) {
                atrMultiplierSL = 2.0;
                atrMultiplierTP = 3.0;
            } else if (decisionRes.marketRegime != null && decisionRes.marketRegime.regime == MarketRegimeResult.Regime.LOW_VOLATILITY) {
                atrMultiplierSL = 1.2;
                atrMultiplierTP = 1.8;
            }

            double calculatedSL = result.entryPrice - (atr * atrMultiplierSL);
            if (support > 0 && support < result.entryPrice && support >= result.entryPrice - (atr * 2.5)) {
                double supportSL = support - (atr * 0.2);
                if (supportSL < result.entryPrice) {
                    calculatedSL = Math.min(calculatedSL, supportSL);
                }
            }
            result.suggestedStopLoss = calculatedSL;

            double calculatedTP = result.entryPrice + (atr * atrMultiplierTP);
            if (resistance > result.entryPrice + (atr * 0.5) && resistance <= result.entryPrice + (atr * 3.5)) {
                double resTP = resistance - (atr * 0.2);
                if (resTP > result.entryPrice + (atr * 1.5)) {
                    calculatedTP = Math.max(calculatedTP, resTP);
                }
            }
            result.suggestedTakeProfit = calculatedTP;

            double riskDist = result.entryPrice - result.suggestedStopLoss;
            double rewardDist = result.suggestedTakeProfit - result.entryPrice;
            if (riskDist > 0) {
                result.suggestedRiskReward = rewardDist / riskDist;
            }

        } else if (decisionRes.decision == TradingDecisionResult.Decision.SELL) {
            result.signalType = SignalType.SELL;

            // Calculate SL and TP for SELL
            double atrMultiplierSL = 1.5;
            double atrMultiplierTP = 2.25;

            if (decisionRes.marketRegime != null && decisionRes.marketRegime.regime == MarketRegimeResult.Regime.HIGH_VOLATILITY) {
                atrMultiplierSL = 2.0;
                atrMultiplierTP = 3.0;
            } else if (decisionRes.marketRegime != null && decisionRes.marketRegime.regime == MarketRegimeResult.Regime.LOW_VOLATILITY) {
                atrMultiplierSL = 1.2;
                atrMultiplierTP = 1.8;
            }

            double calculatedSL = result.entryPrice + (atr * atrMultiplierSL);
            if (resistance > result.entryPrice && resistance <= result.entryPrice + (atr * 2.5)) {
                double resSL = resistance + (atr * 0.2);
                if (resSL > result.entryPrice) {
                    calculatedSL = Math.max(calculatedSL, resSL);
                }
            }
            result.suggestedStopLoss = calculatedSL;

            double calculatedTP = result.entryPrice - (atr * atrMultiplierTP);
            if (support > 0 && support < result.entryPrice - (atr * 0.5) && support >= result.entryPrice - (atr * 3.5)) {
                double supTP = support + (atr * 0.2);
                if (supTP < result.entryPrice - (atr * 1.5)) {
                    calculatedTP = Math.min(calculatedTP, supTP);
                }
            }
            result.suggestedTakeProfit = calculatedTP;

            double riskDist = result.suggestedStopLoss - result.entryPrice;
            double rewardDist = result.entryPrice - result.suggestedTakeProfit;
            if (riskDist > 0) {
                result.suggestedRiskReward = rewardDist / riskDist;
            }

        } else { // WAIT or NO_TRADE
            result.signalType = SignalType.HOLD;
            result.suggestedStopLoss = 0.0;
            result.suggestedTakeProfit = 0.0;
            result.suggestedRiskReward = 0.0;
        }

        // 6. Validate Signal Quality, R:R Ratio, and Confidence Threshold
        if (result.signalType != SignalType.HOLD) {

            // Validate Risk/Reward Ratio
            if (result.suggestedRiskReward < this.minRiskRewardRatio) {
                result.signalType = SignalType.HOLD;
                String reason = String.format(Locale.US,
                        "تحويل الإشارة إلى HOLD: نسبة المخاطرة إلى العائد (%.2f) أقل من الحد الأدنى المقبول (%.2f).",
                        result.suggestedRiskReward, this.minRiskRewardRatio);
                result.conflictingFactors.add(reason);
                result.signalReason = reason;
                result.valid = true;
                return result;
            }

            // Validate Confidence Threshold
            if (result.confidence < this.minConfidenceThreshold) {
                result.signalType = SignalType.HOLD;
                String reason = String.format(Locale.US,
                        "تحويل الإشارة إلى HOLD: نسبة الثقة (%.1f%%) أقل من حد الثقة الأدنى المطلوب (%.1f%%).",
                        result.confidence, this.minConfidenceThreshold);
                result.conflictingFactors.add(reason);
                result.signalReason = reason;
                result.valid = true;
                return result;
            }

            // Validate Signal Quality
            if ("INVALID".equals(decisionRes.signalQuality) || "LOW".equals(decisionRes.signalQuality)) {
                result.signalType = SignalType.HOLD;
                String reason = "تحويل الإشارة إلى HOLD: جودة الإشارة (" + decisionRes.signalQuality + ") غير كافية لفتح صفقة.";
                result.conflictingFactors.add(reason);
                result.signalReason = reason;
                result.valid = true;
                return result;
            }
        }

        result.valid = true;
        result.signalReason = buildSignalReason(result);
        return result;
    }

    /**
     * Converts a SignalResult directly into a TradeSetup object.
     */
    public TradeSetup toTradeSetup(SignalResult signalResult) {
        TradeSetup setup = new TradeSetup();
        if (signalResult == null || !signalResult.valid) {
            setup.valid = false;
            setup.direction = TradeSetup.Direction.NONE;
            String reason = signalResult == null ? "نتيجة محرك الإشارات غير متوفرة (null)." : signalResult.rejectionReason;
            setup.conflictingFactors.add(reason);
            setup.explanation = reason;
            return setup;
        }

        setup.confidence = signalResult.confidence;
        setup.entryPrice = signalResult.entryPrice;
        setup.stopLoss = signalResult.suggestedStopLoss;
        setup.takeProfit = signalResult.suggestedTakeProfit;
        setup.riskRewardRatio = signalResult.suggestedRiskReward;

        if (signalResult.supportingFactors != null) {
            setup.supportingFactors.addAll(signalResult.supportingFactors);
        }
        if (signalResult.conflictingFactors != null) {
            setup.conflictingFactors.addAll(signalResult.conflictingFactors);
        }

        if (signalResult.tradingDecisionResult != null) {
            setup.signalQuality = signalResult.tradingDecisionResult.signalQuality != null ?
                    signalResult.tradingDecisionResult.signalQuality : "INVALID";
            if (signalResult.tradingDecisionResult.marketRegime != null) {
                setup.marketRegime = signalResult.tradingDecisionResult.marketRegime.regime;
            }
        }

        if (signalResult.signalType == SignalType.BUY) {
            setup.direction = TradeSetup.Direction.BUY;
            setup.riskDistance = setup.entryPrice - setup.stopLoss;
            setup.rewardDistance = setup.takeProfit - setup.entryPrice;
            setup.valid = setup.riskRewardRatio >= this.minRiskRewardRatio && setup.riskDistance > 0 && setup.rewardDistance > 0;
        } else if (signalResult.signalType == SignalType.SELL) {
            setup.direction = TradeSetup.Direction.SELL;
            setup.riskDistance = setup.stopLoss - setup.entryPrice;
            setup.rewardDistance = setup.entryPrice - setup.takeProfit;
            setup.valid = setup.riskRewardRatio >= this.minRiskRewardRatio && setup.riskDistance > 0 && setup.rewardDistance > 0;
        } else { // HOLD
            setup.direction = TradeSetup.Direction.NONE;
            setup.valid = false;
        }

        setup.explanation = signalResult.signalReason;
        return setup;
    }

    private void mapTrend(TradingDecisionResult decisionRes, SignalResult result) {
        String decTrend = decisionRes.trend != null ? decisionRes.trend : "";
        if (decTrend.contains("صاعد")) {
            result.trend = Trend.BULLISH;
            result.trendName = "صاعد (UP)";
        } else if (decTrend.contains("هابط")) {
            result.trend = Trend.BEARISH;
            result.trendName = "هابط (DOWN)";
        } else {
            result.trend = Trend.SIDEWAYS;
            result.trendName = "جانبي (SIDEWAYS)";
        }
        result.trendStrength = decisionRes.trendStrength != null ? decisionRes.trendStrength : "ضعيفة (WEAK)";
    }

    private SignalResult createInvalidSignal(String reason) {
        SignalResult res = new SignalResult();
        res.signalType = SignalType.HOLD;
        res.trend = Trend.SIDEWAYS;
        res.valid = false;
        res.rejectionReason = reason;
        res.signalReason = reason;
        res.conflictingFactors.add(reason);
        return res;
    }

    private String buildSignalReason(SignalResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("• نوع الإشارة: ").append(r.signalType.name()).append("\n");
        sb.append("• اتجاه السوق: ").append(r.trendName).append(" (قوة الاتجاه: ").append(r.trendStrength).append(")\n");
        sb.append("• الزخم: ").append(r.momentum).append(" | التقلب: ").append(r.volatility).append("\n");
        sb.append("• نسبة الثقة: ").append(String.format(Locale.US, "%.1f%%", r.confidence)).append("\n");

        if (r.signalType != SignalType.HOLD) {
            sb.append("• سعر الدخول المقترح (Entry): $").append(String.format(Locale.US, "%.2f", r.entryPrice)).append("\n");
            sb.append("• وقف الخسارة المقترح (SL): $").append(String.format(Locale.US, "%.2f", r.suggestedStopLoss)).append("\n");
            sb.append("• هدف الربح المقترح (TP): $").append(String.format(Locale.US, "%.2f", r.suggestedTakeProfit)).append("\n");
            sb.append("• نسبة المخاطرة/العائد المقترحة (R:R): 1 : ").append(String.format(Locale.US, "%.2f", r.suggestedRiskReward)).append("\n");
        } else {
            sb.append("• التوصية الحالية: الانتظار والاحتفاظ (HOLD) حتى توفر إشارة عالية الجودة ومطابقة للشروط.\n");
        }

        return sb.toString();
    }
}
