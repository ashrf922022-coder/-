package com.awridi.ai;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AIDecisionEngine - Central local rule-based AI engine that integrates outputs from
 * Technical Analysis, Signal Engine, Market Regime, Trade Setup, Risk Management Engine,
 * and Kill Switch into a unified decision (BUY, SELL, or WAIT) with strict Risk Gate enforcement.
 *
 * Designed with strict zero Look-Ahead Bias protection for historical backtesting.
 */
public class AIDecisionEngine {

    private final TradingDecisionEngine tradingDecisionEngine = new TradingDecisionEngine();
    private final SignalEngine signalEngine = new SignalEngine();
    private final TradeSetupEngine tradeSetupEngine = new TradeSetupEngine();
    private final SignalConfluenceEngine confluenceEngine = new SignalConfluenceEngine();
    private final MarketRegimeEngine regimeEngine = new MarketRegimeEngine();
    private final RiskManagementEngine riskManagementEngine = new RiskManagementEngine();

    public AIDecisionResult evaluate(List<MarketIntelligenceEngine.Bar> bars, String symbol, String timeframe, SharedPreferences prefs) {
        if (bars == null || bars.isEmpty()) {
            return createInsufficientDataResult(symbol, timeframe, "بيانات السوق فارغة أو غير متوفرة");
        }
        return evaluateAtCandle(bars, bars.size() - 1, symbol, timeframe, prefs);
    }

    public AIDecisionResult evaluateAtCandle(List<MarketIntelligenceEngine.Bar> bars, int endIndex, String symbol, String timeframe, SharedPreferences prefs) {
        if (bars == null || endIndex < 0 || endIndex >= bars.size()) {
            return createInsufficientDataResult(symbol, timeframe, "مؤشر الشمعة المستهدف غير صالح أو خارج نطاق البيانات");
        }

        if (endIndex < 29) {
            return createInsufficientDataResult(symbol, timeframe, "بيانات غير كافية لاتخاذ قرار ذكي (تتطلب 30 شمعة على الأقل)");
        }

        // Slice strictly up to endIndex to guarantee ZERO Look-Ahead Bias
        List<MarketIntelligenceEngine.Bar> slice = new ArrayList<>(bars.subList(0, endIndex + 1));
        int n = slice.size();

        AIDecisionResult result = new AIDecisionResult();
        result.symbol = (symbol != null && !symbol.trim().isEmpty()) ? symbol : "XAU/USD";
        result.timeframe = (timeframe != null && !timeframe.trim().isEmpty()) ? timeframe : "15min";
        result.timestamp = System.currentTimeMillis();

        MarketIntelligenceEngine.Bar latestBar = slice.get(n - 1);
        result.currentPrice = latestBar.close;

        // 1. Evaluate Underlying Engines using slice
        TradingDecisionResult tdResult = tradingDecisionEngine.evaluateAtCandle(slice, n - 1);
        MarketRegimeResult regimeResult = regimeEngine.evaluateAtCandle(slice, n - 1);
        SignalEngine.SignalResult signalResult = signalEngine.generateSignal(slice);
        TradeSetup setup = tradeSetupEngine.createTradeSetupFromSignal(signalResult);

        // Populate Market Regime fields
        if (regimeResult != null) {
            result.marketRegime = regimeResult.regime;
            result.marketRegimeConfidence = regimeResult.confidence;
            result.marketRegimeNameArabic = getRegimeArabicName(regimeResult.regime);
        }

        // 2. Risk Management & Risk Gate Evaluation
        double capital = 10000.0;
        double riskPct = 1.0;
        double maxDailyLossPct = 3.0;
        double todayLossPnl = 0.0;
        boolean killSwitchActive = false;

        if (prefs != null) {
            capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
            riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
            maxDailyLossPct = Double.parseDouble(prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0"));
            PortfolioManager.PortfolioSummary summary = PortfolioManager.calculateSummary(prefs);
            todayLossPnl = summary.todayLossPnl;
            killSwitchActive = KillSwitch.isActive(prefs);
        }

        riskManagementEngine.setMaxDailyLossPercentage(maxDailyLossPct);
        RiskManagementEngine.RiskResult riskResult = riskManagementEngine.evaluateTradeSetupRisk(setup, capital, riskPct, todayLossPnl);

        // Apply Kill Switch check
        if (killSwitchActive) {
            riskResult.valid = false;
            riskResult.rejectionReason = "مفتاح طوارئ الأمان (Kill Switch) مفعل لحماية الحساب من أي تداول جديد.";
        }

        result.riskApproved = riskResult.valid;
        result.rejectionReason = riskResult.rejectionReason;
        result.riskWarnings = riskResult.warnings;

        // 3. Signal Confluence Evaluation
        SignalConfluenceEngine.ConfluenceResult confluence = confluenceEngine.evaluateConfluence(tdResult, signalResult, setup, riskResult);

        result.confluenceScore = confluence.confluenceScore;
        result.confluenceLevel = confluence.confluenceLevel;
        result.supportingSignalsCount = confluence.supportingCount;
        result.conflictingSignalsCount = confluence.conflictingCount;
        result.neutralSignalsCount = confluence.neutralCount;
        result.supportingSignals = confluence.supportingSignals;
        result.conflictingSignals = confluence.conflictingSignals;
        result.neutralSignals = confluence.neutralSignals;

        // 4. Trade Quality Score Calculation
        calculateTradeQualityScore(result, setup, confluence, regimeResult, riskResult);

        // 5. Overall Confidence Calculation
        calculateOverallConfidence(result, confluence, regimeResult, setup, riskResult);

        // 6. Decision Rules & Risk Gate Enforcement
        determineFinalDecision(result, signalResult, setup, confluence, regimeResult, riskResult, killSwitchActive);

        // 7. Populate Proposed Trade Parameters
        if (setup != null) {
            result.entryPrice = setup.entryPrice;
            result.stopLoss = setup.stopLoss;
            result.takeProfit = setup.takeProfit;
            result.riskRewardRatio = setup.riskRewardRatio;
        } else {
            result.entryPrice = result.currentPrice;
            result.stopLoss = result.currentPrice - 5.0;
            result.takeProfit = result.currentPrice + 10.0;
            result.riskRewardRatio = 2.0;
        }
        result.positionSizeLot = riskResult.positionSize;

        // 8. Build Arabic Explanation & Audit Trail
        buildDecisionAuditTrail(result, signalResult, setup, confluence, regimeResult, riskResult);
        buildArabicExplanation(result);

        return result;
    }

    private void calculateTradeQualityScore(
            AIDecisionResult res,
            TradeSetup setup,
            SignalConfluenceEngine.ConfluenceResult confluence,
            MarketRegimeResult regime,
            RiskManagementEngine.RiskResult risk) {

        double score = 0.0;

        // Trend Alignment (20 pts)
        if (confluence.supportingSignals.stream().anyMatch(s -> s.contains("الاتجاه الفني"))) {
            score += 20.0;
        } else if (confluence.neutralSignals.stream().anyMatch(s -> s.contains("الاتجاه الفني"))) {
            score += 10.0;
        }

        // Momentum Alignment (20 pts)
        if (confluence.supportingSignals.stream().anyMatch(s -> s.contains("الزخم"))) {
            score += 20.0;
        } else if (confluence.neutralSignals.stream().anyMatch(s -> s.contains("الزخم"))) {
            score += 10.0;
        }

        // Market Structure & Support/Resistance (15 pts)
        if (confluence.supportingSignals.stream().anyMatch(s -> s.contains("بنية السعر"))) {
            score += 15.0;
        } else if (confluence.neutralSignals.stream().anyMatch(s -> s.contains("بنية السعر"))) {
            score += 7.0;
        }

        // Stop Loss & Risk Reward Quality (20 pts)
        if (setup != null && setup.riskRewardRatio >= 2.0) {
            score += 20.0;
        } else if (setup != null && setup.riskRewardRatio >= 1.5) {
            score += 14.0;
        } else if (setup != null && setup.riskRewardRatio >= 1.0) {
            score += 8.0;
        }

        // Volatility & Regime Fit (15 pts)
        if (regime != null && (regime.regime == MarketRegimeResult.Regime.TREND_UP || regime.regime == MarketRegimeResult.Regime.TREND_DOWN)) {
            score += 15.0;
        } else if (regime != null && regime.regime == MarketRegimeResult.Regime.RANGE) {
            score += 10.0;
        } else if (regime != null && regime.regime == MarketRegimeResult.Regime.TRANSITION) {
            score += 5.0;
        }

        // Risk Gate Approval (10 pts)
        if (risk != null && risk.valid) {
            score += 10.0;
        }

        res.tradeQualityScore = Math.max(0.0, Math.min(100.0, score));

        if (res.tradeQualityScore >= 78.0) {
            res.tradeQuality = AIDecisionResult.TradeQuality.HIGH_QUALITY;
        } else if (res.tradeQualityScore >= 52.0) {
            res.tradeQuality = AIDecisionResult.TradeQuality.MEDIUM_QUALITY;
        } else {
            res.tradeQuality = AIDecisionResult.TradeQuality.LOW_QUALITY;
        }
    }

    private void calculateOverallConfidence(
            AIDecisionResult res,
            SignalConfluenceEngine.ConfluenceResult confluence,
            MarketRegimeResult regime,
            TradeSetup setup,
            RiskManagementEngine.RiskResult risk) {

        // Components: Confluence (40%), Regime Confidence (20%), Setup Quality (20%), Risk Approval (20%)
        double confluenceWeight = confluence.confluenceScore * 0.40;
        double regimeWeight = (regime != null ? regime.confidence : 50.0) * 0.20;
        double qualityWeight = res.tradeQualityScore * 0.20;
        double riskWeight = (risk != null && risk.valid) ? 20.0 : 0.0;

        res.confidence = Math.max(0.0, Math.min(100.0, confluenceWeight + regimeWeight + qualityWeight + riskWeight));

        res.confidenceExplanation = String.format(
                Locale.US,
                "تتكون درجة الثقة (%.1f%%) من: توافق الإشارات (%.1f%%) + ثقة حالة السوق (%.1f%%) + جودة الإعداد (%.1f%%) + موافقة إدارة المخاطر (%.1f%%). (ملاحظة: الثقة ليست احتمالية إحصائية معصومة للربح).",
                res.confidence, confluenceWeight, regimeWeight, qualityWeight, riskWeight
        );
    }

    private void determineFinalDecision(
            AIDecisionResult res,
            SignalEngine.SignalResult signalResult,
            TradeSetup setup,
            SignalConfluenceEngine.ConfluenceResult confluence,
            MarketRegimeResult regime,
            RiskManagementEngine.RiskResult risk,
            boolean killSwitchActive) {

        res.decisionReasons.clear();

        // 1. STRICT RISK GATE ENFORCEMENT
        if (killSwitchActive) {
            res.decision = AIDecisionResult.Decision.WAIT;
            res.decisionReasons.add("مفتاح الطوارئ (Kill Switch) مفعل - تجميد جميع التداولات لحماية الرصيد.");
            return;
        }

        if (risk == null || !risk.valid) {
            res.decision = AIDecisionResult.Decision.WAIT;
            res.decisionReasons.add("تم رفض الصفقة بواسطة بوابة إدارة المخاطر (Risk Gate Rejection): " + (risk != null ? risk.rejectionReason : "بيانات المخاطر غير متوفرة"));
            return;
        }

        // 2. Regime Filter (In HIGH_VOLATILITY or UNCERTAIN regimes, require higher confidence)
        if (regime != null && regime.regime == MarketRegimeResult.Regime.HIGH_VOLATILITY) {
            if (res.confidence < 75.0 || confluence.conflictingCount > 0) {
                res.decision = AIDecisionResult.Decision.WAIT;
                res.decisionReasons.add("حالة السوق تقلب شديد مرتفع (HIGH_VOLATILITY) - تم تطبيق فلتر حماية المخاطر وتشديد شروط التوافق.");
                return;
            }
        }

        if (regime != null && regime.regime == MarketRegimeResult.Regime.UNCERTAIN) {
            if (res.confidence < 70.0) {
                res.decision = AIDecisionResult.Decision.WAIT;
                res.decisionReasons.add("حالة السوق غير واضحة (UNCERTAIN) - تم تفضيل الانتظار (WAIT) لعدم الوضوح الفني.");
                return;
            }
        }

        // 3. Technical Signal & Confluence Check
        if (signalResult == null || signalResult.signalType == SignalEngine.SignalType.HOLD) {
            res.decision = AIDecisionResult.Decision.WAIT;
            res.decisionReasons.add("محرك الإشارات الفنية ينصح بالانتظار (HOLD).");
            return;
        }

        if (confluence.conflictingCount >= 2 || confluence.confluenceScore < 55.0) {
            res.decision = AIDecisionResult.Decision.WAIT;
            res.decisionReasons.add("وجود تضارب في الإشارات الفنية (" + confluence.conflictingCount + " إشارات متعارضة) أو انخفاض درجة التوافق (" + String.format(Locale.US, "%.1f%%", confluence.confluenceScore) + ").");
            return;
        }

        // 4. Set BUY / SELL if all conditions met
        if (signalResult.signalType == SignalEngine.SignalType.BUY) {
            res.decision = AIDecisionResult.Decision.BUY;
            res.decisionReasons.add("توافق إيجابي قوي بين الاتجاه والصعود، مع اجتياز كامل شروط إدارة المخاطر.");
            res.decisionReasons.add("جودة إعداد الصفقة: " + res.tradeQuality.getArabicName());
        } else if (signalResult.signalType == SignalEngine.SignalType.SELL) {
            res.decision = AIDecisionResult.Decision.SELL;
            res.decisionReasons.add("توافق سلبي هابط قوي بين الاتجاه والزخم، مع اجتياز كامل شروط إدارة المخاطر.");
            res.decisionReasons.add("جودة إعداد الصفقة: " + res.tradeQuality.getArabicName());
        } else {
            res.decision = AIDecisionResult.Decision.WAIT;
            res.decisionReasons.add("عدم توفر شروط كافية لتأكيد اتجاه الصفقة.");
        }
    }

    private void buildDecisionAuditTrail(
            AIDecisionResult res,
            SignalEngine.SignalResult signal,
            TradeSetup setup,
            SignalConfluenceEngine.ConfluenceResult confluence,
            MarketRegimeResult regime,
            RiskManagementEngine.RiskResult risk) {

        res.auditTrail.clear();
        res.auditTrail.add("1. Symbol: " + res.symbol + " | Timeframe: " + res.timeframe);
        res.auditTrail.add("2. Current Price: $" + String.format(Locale.US, "%.2f", res.currentPrice));
        res.auditTrail.add("3. Market Regime: " + (regime != null ? regime.regime.name() : "UNKNOWN") + " (Confidence: " + String.format(Locale.US, "%.1f%%", regime != null ? regime.confidence : 0) + ")");
        res.auditTrail.add("4. Technical Signal Engine: " + (signal != null ? signal.signalType.name() : "NONE"));
        res.auditTrail.add("5. Trade Setup: " + (setup != null && setup.valid ? "VALID (" + setup.direction.name() + ")" : "INVALID"));
        res.auditTrail.add("6. Signal Confluence Score: " + String.format(Locale.US, "%.1f%%", confluence.confluenceScore) + " | Level: " + confluence.confluenceLevel);
        res.auditTrail.add("7. Supporting Signals: " + confluence.supportingCount + " | Conflicting: " + confluence.conflictingCount + " | Neutral: " + confluence.neutralCount);
        res.auditTrail.add("8. Trade Quality Rating: " + res.tradeQuality.name() + " (" + String.format(Locale.US, "%.1f", res.tradeQualityScore) + "/100)");
        res.auditTrail.add("9. Risk Gate Approval: " + (risk != null && risk.valid ? "APPROVED ✅" : "REJECTED ❌ (" + (risk != null ? risk.rejectionReason : "No Data") + ")"));
        res.auditTrail.add("10. Overall Decision: " + res.decision.name() + " | Confidence: " + String.format(Locale.US, "%.1f%%", res.confidence));
    }

    private void buildArabicExplanation(AIDecisionResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 قرار محرك الذكاء الاصطناعي (AI Decision): ").append(res.decision.name()).append("\n");
        sb.append("• نسبة الثقة: ").append(String.format(Locale.US, "%.1f%%", res.confidence)).append("\n");
        sb.append("• جودة إعداد الصفقة (Trade Quality): ").append(res.tradeQuality.getArabicName()).append("\n");
        sb.append("• حالة السوق (Market Regime): ").append(res.marketRegimeNameArabic).append("\n");
        sb.append("• درجة توافق الإشارات (Confluence): ").append(res.confluenceLevel).append(" (").append(String.format(Locale.US, "%.1f%%", res.confluenceScore)).append(")\n");
        sb.append("• حالة إدارة المخاطر (Risk Gate): ").append(res.riskApproved ? "مقبول ✅" : "مرفوض ❌").append("\n");

        if (!res.decisionReasons.isEmpty()) {
            sb.append("\nأسباب القرار:\n");
            for (String reason : res.decisionReasons) {
                sb.append(" - ").append(reason).append("\n");
            }
        }

        if (!res.riskApproved && !res.rejectionReason.isEmpty()) {
            sb.append("\nسبب رفض/حظر الصفقة:\n - ").append(res.rejectionReason).append("\n");
        }

        if (!res.riskWarnings.isEmpty()) {
            sb.append("\nتحذيرات المخاطر:\n");
            for (String warn : res.riskWarnings) {
                sb.append(" ⚠️ ").append(warn).append("\n");
            }
        }

        res.arabicExplanation = sb.toString();
    }

    private AIDecisionResult createInsufficientDataResult(String symbol, String timeframe, String reasonArabic) {
        AIDecisionResult res = new AIDecisionResult();
        res.symbol = symbol != null ? symbol : "XAU/USD";
        res.timeframe = timeframe != null ? timeframe : "15min";
        res.decision = AIDecisionResult.Decision.WAIT;
        res.confidence = 0.0;
        res.tradeQuality = AIDecisionResult.TradeQuality.LOW_QUALITY;
        res.riskApproved = false;
        res.rejectionReason = reasonArabic;
        res.decisionReasons.add(reasonArabic);
        res.arabicExplanation = "قرار الانتظار (WAIT) لعدم كفاية بيانات السوق للتحليل والتدقيق.";
        return res;
    }

    private String getRegimeArabicName(MarketRegimeResult.Regime regime) {
        if (regime == null) return "غير محدد";
        switch (regime) {
            case TREND_UP:
                return "اتجاه صاعد منتظم (TRENDING UP)";
            case TREND_DOWN:
                return "اتجاه هابط ضاغط (TRENDING DOWN)";
            case RANGE:
                return "نطاق عرضي متوازن (RANGING)";
            case HIGH_VOLATILITY:
                return "تقلب شديد مرتفع (HIGH VOLATILITY)";
            case LOW_VOLATILITY:
                return "خمول / تقلب ضعيف (LOW VOLATILITY)";
            case TRANSITION:
                return "مرحلة انتقال / تصحيح (TRANSITION)";
            default:
                return "غير محدد / غير واضح (UNCERTAIN)";
        }
    }
}
