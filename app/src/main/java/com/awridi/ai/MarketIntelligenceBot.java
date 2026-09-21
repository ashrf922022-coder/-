package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Master Market Intelligence Bot for XAU/USD.
 * Synthesizes Historical Data, Feature Engineering, Market State, Historical Similarity,
 * Multi-Timeframe Alignment, Backtesting, Walk-Forward, and Risk Rules into a unified intelligence output.
 */
public class MarketIntelligenceBot {

    public static class MarketIntelligenceReport {
        public String symbol = "XAU/USD";
        public double currentPrice;
        public MarketStateEngine.MarketState marketState;
        public MultiTimeframeEngine.MTFAlignment mtfAlignment;
        public PatternIntelligenceEngine.PatternResult patternResult;
        public HistoricalSimilarityEngine.SimilarityReport similarityReport;
        public WalkForwardEngine.WalkForwardReport walkForwardReport;
        public String decision; // BUY SETUP, SELL SETUP, WAIT
        public String decisionReason;
        public double empiricalBullishPercentage;
        public int historicalSampleSize;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit1;
        public double takeProfit2;
        public double riskRewardRatio;
        public double suggestedLotSize;
        public String confirmationCondition;
        public String invalidationCondition;
        public String fullArabicSummary;
    }

    public static MarketIntelligenceReport generateIntelligence(Map<String, List<GoldAnalysisEngine.Bar>> mtfBars, SharedPreferences prefs) {
        MarketIntelligenceReport report = new MarketIntelligenceReport();

        List<GoldAnalysisEngine.Bar> bars15m = mtfBars.get("15min");
        if (bars15m == null || bars15m.isEmpty()) {
            if (!mtfBars.isEmpty()) {
                bars15m = mtfBars.values().iterator().next();
            }
        }

        if (bars15m == null || bars15m.isEmpty()) {
            report.decision = "WAIT";
            report.decisionReason = "بيانات الأسعار غير متوفرة حالياً";
            return report;
        }

        // Clean & process bars
        HistoricalDataEngine.CleanedSeries cleaned15m = HistoricalDataEngine.processAndCleanSeries("15m", bars15m);
        List<GoldAnalysisEngine.Bar> cleanBars = cleaned15m.bars;
        int n = cleanBars.size();
        int curIdx = n - 1;

        GoldAnalysisEngine.Bar latestBar = cleanBars.get(curIdx);
        report.currentPrice = latestBar.c;

        // Extract features & market state
        FeatureEngine.FeatureVector features = FeatureEngine.extractFeatures(cleanBars, curIdx);
        report.marketState = MarketStateEngine.evaluateMarketState(cleanBars, features);

        // MTF Alignment
        report.mtfAlignment = MultiTimeframeEngine.evaluateAlignment(mtfBars);

        // Pattern Intelligence
        report.patternResult = PatternIntelligenceEngine.detectPattern(cleanBars, features);

        // Historical Similarity Match (top 15 cases)
        report.similarityReport = HistoricalSimilarityEngine.findSimilarHistoricalContexts(cleanBars, curIdx, 15);
        report.historicalSampleSize = report.similarityReport.sampleSize;
        report.empiricalBullishPercentage = report.similarityReport.empiricalBullishOutcomePct;

        // Walk-Forward Analysis
        report.walkForwardReport = WalkForwardEngine.runWalkForwardAnalysis(cleanBars, prefs);

        // Signal synthesis
        boolean isBuySetup = report.mtfAlignment.isFullyAligned && report.mtfAlignment.alignmentSummary.contains("صاعد") &&
                             report.marketState.primaryRegime == MarketStateEngine.Regime.TREND_UP &&
                             report.empiricalBullishPercentage >= 60.0;

        boolean isSellSetup = report.mtfAlignment.isFullyAligned && report.mtfAlignment.alignmentSummary.contains("هابط") &&
                              report.marketState.primaryRegime == MarketStateEngine.Regime.TREND_DOWN &&
                              report.empiricalBullishPercentage <= 40.0;

        double atr = Math.max(1.5, features.atr14);

        if (isBuySetup) {
            report.decision = "BUY SETUP 🟢";
            report.entryPrice = report.currentPrice;
            report.stopLoss = report.entryPrice - (1.5 * atr);
            report.takeProfit1 = report.entryPrice + (1.5 * atr);
            report.takeProfit2 = report.entryPrice + (3.0 * atr);
            report.confirmationCondition = "إغلاق شمعة 15 دقيقة أعلى من $" + String.format(Locale.US, "%.2f", report.entryPrice + (0.5 * atr));
            report.invalidationCondition = "كسر وإغلاق أسفل مستوى وقف الخسارة $" + String.format(Locale.US, "%.2f", report.stopLoss);
            report.decisionReason = "توافق صاعد عبر جميع الأطر الزمنية مع نسبة حدوث تاريخية إيجابية قدرها " + String.format(Locale.US, "%.1f", report.empiricalBullishPercentage) + "% بين " + report.historicalSampleSize + " حالة مشابهة.";
        } else if (isSellSetup) {
            report.decision = "SELL SETUP 🔴";
            report.entryPrice = report.currentPrice;
            report.stopLoss = report.entryPrice + (1.5 * atr);
            report.takeProfit1 = report.entryPrice - (1.5 * atr);
            report.takeProfit2 = report.entryPrice - (3.0 * atr);
            report.confirmationCondition = "إغلاق شمعة 15 دقيقة أسفل من $" + String.format(Locale.US, "%.2f", report.entryPrice - (0.5 * atr));
            report.invalidationCondition = "اختراق وإغلاق أعلى مستوى وقف الخسارة $" + String.format(Locale.US, "%.2f", report.stopLoss);
            report.decisionReason = "توافق هابط عبر جميع الأطر الزمنية مع نسبة حدوث تاريخية سلبية قدرها " + String.format(Locale.US, "%.1f", 100.0 - report.empiricalBullishPercentage) + "% بين " + report.historicalSampleSize + " حالة مشابهة.";
        } else {
            report.decision = "WAIT ⏳";
            report.entryPrice = report.currentPrice;
            report.stopLoss = 0; report.takeProfit1 = 0; report.takeProfit2 = 0;
            report.confirmationCondition = "انتظار اكتمال التوافق بين الأطر الزمنية وتجاوز نسبة التوافق التاريخي 60%";
            report.invalidationCondition = "عدم التداول في ظروف النطاق العرضي أو الشك";
            report.decisionReason = "الأدلة التاريخية والتوافق بين الأطر الزمنية غير كافية حالياً لجزم إشارة آمنة.";
        }

        // Calculate Risk & Lot
        if (report.stopLoss > 0) {
            double riskDiff = Math.abs(report.entryPrice - report.stopLoss);
            report.riskRewardRatio = Math.abs(report.takeProfit1 - report.entryPrice) / Math.max(0.1, riskDiff);

            double capital = 10000;
            double riskPct = 1.0;
            if (prefs != null) {
                capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
                riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
            }
            double maxRiskUsd = capital * (riskPct / 100.0);
            report.suggestedLotSize = maxRiskUsd / (riskDiff * 100.0);
        }

        report.fullArabicSummary = buildArabicSummary(report);
        return report;
    }

    private static String buildArabicSummary(MarketIntelligenceReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 ذكاء السوق | ").append(r.symbol).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("• السعر الحالي: $").append(String.format(Locale.US, "%.2f", r.currentPrice)).append("\n");
        sb.append("• حالة السوق: ").append(r.marketState.trendDescription).append("\n");
        sb.append("• توافق الأطر: ").append(r.mtfAlignment.alignmentSummary).append("\n");
        sb.append("• النمط الفني: ").append(r.patternResult.arabicDescription).append("\n");
        sb.append("• التشابه التاريخي: ").append(String.format(Locale.US, "%.1f", r.empiricalBullishPercentage)).append("% حدوث صاعد عبر ").append(r.historicalSampleSize).append(" حالة مشابهة\n");
        sb.append("• القرار: ").append(r.decision).append("\n");
        sb.append("• السبب: ").append(r.decisionReason).append("\n");
        if (r.stopLoss > 0) {
            sb.append("• سعر الدخول: $").append(String.format(Locale.US, "%.2f", r.entryPrice)).append("\n");
            sb.append("• وقف الخسارة: $").append(String.format(Locale.US, "%.2f", r.stopLoss)).append("\n");
            sb.append("• الهدف الأول: $").append(String.format(Locale.US, "%.2f", r.takeProfit1)).append("\n");
            sb.append("• عائد/مخاطرة: 1:").append(String.format(Locale.US, "%.2f", r.riskRewardRatio)).append("\n");
            sb.append("• العقد المقترح: ").append(String.format(Locale.US, "%.2f", r.suggestedLotSize)).append(" لوت\n");
        }
        sb.append("• شرط التأكيد: ").append(r.confirmationCondition).append("\n");
        sb.append("• شرط الإلغاء: ").append(r.invalidationCondition).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }
}
