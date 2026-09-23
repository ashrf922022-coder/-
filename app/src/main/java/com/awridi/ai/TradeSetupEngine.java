package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TradeSetupEngine {

    public static final double DEFAULT_MIN_RR = 1.5;
    private double minRiskRewardRatio = DEFAULT_MIN_RR;

    public TradeSetupEngine() {
        this.minRiskRewardRatio = DEFAULT_MIN_RR;
    }

    public TradeSetupEngine(double minRiskRewardRatio) {
        setMinRiskRewardRatio(minRiskRewardRatio);
    }

    public double getMinRiskRewardRatio() {
        return minRiskRewardRatio;
    }

    public void setMinRiskRewardRatio(double minRiskRewardRatio) {
        if (Double.isNaN(minRiskRewardRatio) || Double.isInfinite(minRiskRewardRatio) || minRiskRewardRatio <= 0.0) {
            this.minRiskRewardRatio = DEFAULT_MIN_RR;
        } else {
            this.minRiskRewardRatio = minRiskRewardRatio;
        }
    }

    /**
     * Evaluates bars up to the end of the list and creates a TradeSetup.
     */
    public TradeSetup createTradeSetup(List<MarketIntelligenceEngine.Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return createInvalidSetup("قائمة الشموع فارغة أو غير متوفرة (null/empty).");
        }
        TradingDecisionEngine decisionEngine = new TradingDecisionEngine();
        TradingDecisionResult decisionResult = decisionEngine.evaluate(bars);
        return createTradeSetup(decisionResult, bars);
    }

    /**
     * Evaluates historical bars strictly up to targetIndex to prevent Look-Ahead Bias.
     */
    public TradeSetup evaluateAtCandle(List<MarketIntelligenceEngine.Bar> bars, int targetIndex) {
        if (bars == null || targetIndex < 0 || targetIndex >= bars.size()) {
            return createInvalidSetup("مؤشر الشمعة المستهدف غير صالح أو خارج نطاق البيانات.");
        }
        TradingDecisionEngine decisionEngine = new TradingDecisionEngine();
        TradingDecisionResult decisionResult = decisionEngine.evaluateAtCandle(bars, targetIndex);
        List<MarketIntelligenceEngine.Bar> slicedBars = new ArrayList<>(bars.subList(0, targetIndex + 1));
        return createTradeSetup(decisionResult, slicedBars);
    }

    /**
     * Core logic converting a TradingDecisionResult and historical bars into a complete TradeSetup.
     */
    public TradeSetup createTradeSetup(TradingDecisionResult decisionResult, List<MarketIntelligenceEngine.Bar> bars) {
        TradeSetup setup = new TradeSetup();

        // Ensure safe minRiskRewardRatio value
        setMinRiskRewardRatio(this.minRiskRewardRatio);

        // 1. Check for null or invalid inputs
        if (decisionResult == null) {
            return createInvalidSetup("نتيجة قرار التداول غير متوفرة (null).");
        }

        setup.confidence = decisionResult.confidence;
        setup.signalQuality = decisionResult.signalQuality != null ? decisionResult.signalQuality : "INVALID";
        if (decisionResult.marketRegime != null) {
            setup.marketRegime = decisionResult.marketRegime.regime;
        }

        if (decisionResult.supportingFactors != null) {
            setup.supportingFactors.addAll(decisionResult.supportingFactors);
        }
        if (decisionResult.conflictingFactors != null) {
            setup.conflictingFactors.addAll(decisionResult.conflictingFactors);
        }

        // 2. Validate Decision state (NO_TRADE / WAIT)
        if (decisionResult.decision == TradingDecisionResult.Decision.NO_TRADE ||
            decisionResult.decision == TradingDecisionResult.Decision.WAIT ||
            decisionResult.decision == null) {
            setup.direction = TradeSetup.Direction.NONE;
            setup.valid = false;
            String reason = decisionResult.decision == TradingDecisionResult.Decision.WAIT
                    ? "القرار الحالي هو الانتظار (WAIT) لعدم توفر شروط دخول مكتملة."
                    : "القرار الحالي هو عدم التداول (NO_TRADE).";
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        // 3. Map Decision to Direction (BUY / SELL)
        if (decisionResult.decision == TradingDecisionResult.Decision.BUY) {
            setup.direction = TradeSetup.Direction.BUY;
        } else if (decisionResult.decision == TradingDecisionResult.Decision.SELL) {
            setup.direction = TradeSetup.Direction.SELL;
        } else {
            setup.direction = TradeSetup.Direction.NONE;
            setup.valid = false;
            setup.explanation = buildExplanation(setup, "اتجاه الصفقة غير محدد.");
            return setup;
        }

        // 4. Validate Signal Quality
        if ("INVALID".equals(setup.signalQuality) || "LOW".equals(setup.signalQuality)) {
            setup.valid = false;
            String reason = "جودة الإشارة (" + setup.signalQuality + ") غير كافية لإنشاء إعداد صفقة صالح.";
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        // 5. Validate Prices & Indicators
        double entry = decisionResult.currentPrice;
        double atr = decisionResult.atrValue;
        double support = decisionResult.support;
        double resistance = decisionResult.resistance;

        if (Double.isNaN(entry) || Double.isInfinite(entry) || entry <= 0 ||
            Double.isNaN(atr) || Double.isInfinite(atr) || atr <= 0) {
            setup.valid = false;
            String reason = "بيانات الأسعار أو المؤشرات (Entry/ATR) غير صالحة أو غير معرفة (NaN/Infinity).";
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        setup.entryPrice = entry;

        // Check for invalid Support/Resistance boundary levels relative to Entry
        if (setup.direction == TradeSetup.Direction.BUY) {
            if (support >= entry) {
                setup.valid = false;
                String reason = "وقف الخسارة يجب أن يكون أقل من سعر الدخول لصفقات الشراء (BUY SL < Entry). الدعم المفترض (" + support + ") أعلى من أو يساوي الدخول (" + entry + ").";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
            if (resistance > 0 && resistance <= entry) {
                setup.valid = false;
                String reason = "هدف الربح يجب أن يكون أعلى من سعر الدخول لصفقات الشراء (BUY TP > Entry). المقاومة المفترضة (" + resistance + ") أقل من أو تساوي الدخول (" + entry + ").";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
        } else if (setup.direction == TradeSetup.Direction.SELL) {
            if (resistance > 0 && resistance <= entry) {
                setup.valid = false;
                String reason = "وقف الخسارة يجب أن يكون أعلى من سعر الدخول لصفقات البيع (SELL SL > Entry). المقاومة المفترضة (" + resistance + ") أقل من أو تساوي الدخول (" + entry + ").";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
            if (support >= entry) {
                setup.valid = false;
                String reason = "هدف الربح يجب أن يكون أقل من سعر الدخول لصفقات البيع (SELL TP < Entry). الدعم المفترض (" + support + ") أعلى من أو يساوي الدخول (" + entry + ").";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
        }

        // 6. Calculate Stop Loss & Take Profit based on Direction, Support/Resistance, and ATR
        double atrMultiplierSL = 1.5;
        double atrMultiplierTP = 2.25;

        if (setup.marketRegime == MarketRegimeResult.Regime.HIGH_VOLATILITY) {
            atrMultiplierSL = 2.0;
            atrMultiplierTP = 3.0;
        } else if (setup.marketRegime == MarketRegimeResult.Regime.LOW_VOLATILITY) {
            atrMultiplierSL = 1.2;
            atrMultiplierTP = 1.8;
        }

        if (setup.direction == TradeSetup.Direction.BUY) {
            double calculatedSL = entry - (atr * atrMultiplierSL);
            if (support > 0 && support < entry) {
                double supportSL = support - (atr * 0.2);
                if (supportSL < entry) {
                    calculatedSL = Math.min(calculatedSL, supportSL);
                }
            }
            setup.stopLoss = calculatedSL;

            double calculatedTP = entry + (atr * atrMultiplierTP);
            if (resistance > entry) {
                double resTP = resistance - (atr * 0.2);
                if (resTP > entry) {
                    calculatedTP = resTP;
                }
            }
            setup.takeProfit = calculatedTP;

        } else if (setup.direction == TradeSetup.Direction.SELL) {
            double calculatedSL = entry + (atr * atrMultiplierSL);
            if (resistance > entry) {
                double resSL = resistance + (atr * 0.2);
                if (resSL > entry) {
                    calculatedSL = Math.max(calculatedSL, resSL);
                }
            }
            setup.stopLoss = calculatedSL;

            double calculatedTP = entry - (atr * atrMultiplierTP);
            if (support > 0 && support < entry) {
                double supTP = support + (atr * 0.2);
                if (supTP < entry) {
                    calculatedTP = supTP;
                }
            }
            setup.takeProfit = calculatedTP;
        }

        // 7. Validate SL & TP Boundaries
        if (Double.isNaN(setup.stopLoss) || Double.isInfinite(setup.stopLoss) ||
            Double.isNaN(setup.takeProfit) || Double.isInfinite(setup.takeProfit)) {
            setup.valid = false;
            String reason = "مستويات وقف الخسارة أو أخذ الربح غير صالحة (NaN/Infinity).";
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        if (setup.direction == TradeSetup.Direction.BUY) {
            if (setup.stopLoss >= setup.entryPrice) {
                setup.valid = false;
                String reason = "وقف الخسارة يجب أن يكون أقل من سعر الدخول لصفقات الشراء (BUY SL < Entry).";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
            if (setup.takeProfit <= setup.entryPrice) {
                setup.valid = false;
                String reason = "هدف الربح يجب أن يكون أعلى من سعر الدخول لصفقات الشراء (BUY TP > Entry).";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
        } else if (setup.direction == TradeSetup.Direction.SELL) {
            if (setup.stopLoss <= setup.entryPrice) {
                setup.valid = false;
                String reason = "وقف الخسارة يجب أن يكون أعلى من سعر الدخول لصفقات البيع (SELL SL > Entry).";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
            if (setup.takeProfit >= setup.entryPrice) {
                setup.valid = false;
                String reason = "هدف الربح يجب أن يكون أقل من سعر الدخول لصفقات البيع (SELL TP < Entry).";
                setup.conflictingFactors.add(reason);
                setup.explanation = buildExplanation(setup, reason);
                return setup;
            }
        }

        // 8. Calculate Risk Distance, Reward Distance, and Risk/Reward Ratio
        if (setup.direction == TradeSetup.Direction.BUY) {
            setup.riskDistance = setup.entryPrice - setup.stopLoss;
            setup.rewardDistance = setup.takeProfit - setup.entryPrice;
        } else { // SELL
            setup.riskDistance = setup.stopLoss - setup.entryPrice;
            setup.rewardDistance = setup.entryPrice - setup.takeProfit;
        }

        if (setup.riskDistance <= 0.0000001 || Double.isNaN(setup.riskDistance) || Double.isInfinite(setup.riskDistance)) {
            setup.valid = false;
            String reason = "مسافة المخاطرة غير صالحة أو تساوي الصفر (Risk <= 0).";
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        setup.riskRewardRatio = setup.rewardDistance / setup.riskDistance;

        // 9. Minimum Risk/Reward Validation
        if (setup.riskRewardRatio < this.minRiskRewardRatio) {
            setup.valid = false;
            String reason = String.format(Locale.US,
                    "نسبة المخاطرة إلى العائد (%.2f) أقل من الحد الأدنى المقبول (%.2f).",
                    setup.riskRewardRatio, this.minRiskRewardRatio);
            setup.conflictingFactors.add(reason);
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        // 10. Validate Market Regime consistency with Setup Direction
        boolean regimeConflict = false;
        if (setup.marketRegime == MarketRegimeResult.Regime.TREND_DOWN && setup.direction == TradeSetup.Direction.BUY) {
            regimeConflict = true;
            setup.conflictingFactors.add("حالة السوق اتجاه هابط (TREND_DOWN) بينما قرار الصفقة شراء (BUY).");
        } else if (setup.marketRegime == MarketRegimeResult.Regime.TREND_UP && setup.direction == TradeSetup.Direction.SELL) {
            regimeConflict = true;
            setup.conflictingFactors.add("حالة السوق اتجاه صاعد (TREND_UP) بينما قرار الصفقة بيع (SELL).");
        } else if (setup.marketRegime == MarketRegimeResult.Regime.TRANSITION || setup.marketRegime == MarketRegimeResult.Regime.UNCERTAIN) {
            setup.confidence = Math.max(0.0, setup.confidence - 15.0);
            setup.conflictingFactors.add("حالة السوق في مرحلة انتقال أو غير محددة (" + setup.marketRegime + "). تم تخفيض الثقة.");
        } else if (setup.marketRegime == MarketRegimeResult.Regime.RANGE) {
            setup.supportingFactors.add("السوق في نطاق عرضي (RANGE) — يجب توخي الحذر عند حدود النطاق.");
        } else if (setup.marketRegime == MarketRegimeResult.Regime.TREND_UP && setup.direction == TradeSetup.Direction.BUY) {
            setup.supportingFactors.add("حالة السوق اتجاه صاعد (TREND_UP) تتوافق مع إعداد الشراء.");
        } else if (setup.marketRegime == MarketRegimeResult.Regime.TREND_DOWN && setup.direction == TradeSetup.Direction.SELL) {
            setup.supportingFactors.add("حالة السوق اتجاه هابط (TREND_DOWN) تتوافق مع إعداد البيع.");
        }

        if (regimeConflict && setup.confidence < 75.0) {
            setup.valid = false;
            String reason = "تعارض جوهري بين حالة السوق (Market Regime) واتجاه الصفقة مع مستوى ثقة منخفض.";
            setup.explanation = buildExplanation(setup, reason);
            return setup;
        }

        // 11. Final validity check
        setup.valid = true;
        setup.explanation = buildExplanation(setup, "إعداد صفقة صالح ومكتمل الشروط وفقًا للتحليل الحالي.");

        return setup;
    }

    private TradeSetup createInvalidSetup(String reason) {
        TradeSetup setup = new TradeSetup();
        setup.direction = TradeSetup.Direction.NONE;
        setup.valid = false;
        setup.conflictingFactors.add(reason);
        setup.explanation = buildExplanation(setup, reason);
        return setup;
    }

    private String buildExplanation(TradeSetup setup, String primaryStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("• حالة إعداد الصفقة: ").append(setup.valid ? "صالح (VALID)" : "غير صالح (INVALID)").append("\n");
        sb.append("• حالة النظام الرئيسية: ").append(primaryStatus).append("\n");
        sb.append("• اتجاه الصفقة: ").append(setup.direction.name()).append("\n");
        sb.append("• جودة الإشارة: ").append(setup.signalQuality).append("\n");
        sb.append("• نسبة الثقة: ").append(String.format(Locale.US, "%.1f%%", setup.confidence)).append("\n");
        if (setup.marketRegime != null) {
            sb.append("• حالة السوق (Market Regime): ").append(setup.marketRegime.name()).append("\n");
        }

        if (setup.valid || setup.entryPrice > 0) {
            sb.append("• سعر الدخول (Entry): $").append(String.format(Locale.US, "%.2f", setup.entryPrice)).append("\n");
            sb.append("• وقف الخسارة (Stop Loss): $").append(String.format(Locale.US, "%.2f", setup.stopLoss)).append(" (مسافة: ").append(String.format(Locale.US, "%.2f", setup.riskDistance)).append(")\n");
            sb.append("• هدف الربح (Take Profit): $").append(String.format(Locale.US, "%.2f", setup.takeProfit)).append(" (مسافة: ").append(String.format(Locale.US, "%.2f", setup.rewardDistance)).append(")\n");
            sb.append("• نسبة المخاطرة/العائد (R:R): 1 : ").append(String.format(Locale.US, "%.2f", setup.riskRewardRatio)).append("\n");
        }

        if (!setup.supportingFactors.isEmpty()) {
            sb.append("\nالعوامل الداعمة للإعداد:\n");
            for (String factor : setup.supportingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        if (!setup.conflictingFactors.isEmpty()) {
            sb.append("\nالعوامل المتعارضة / أسباب عدم الصلاحية:\n");
            for (String factor : setup.conflictingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        return sb.toString();
    }
}
