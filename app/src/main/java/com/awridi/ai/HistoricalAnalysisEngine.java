package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 6: Historical Analysis Engine
 * Allows historical evaluation of signals across time series data strictly
 * without Look-Ahead Bias. Extensible for advanced historical backtesting.
 */
public class HistoricalAnalysisEngine {

    public static class HistoricalSignalRecord {
        public int candleIndex;
        public SignalEngine.SignalType signalType;
        public SignalEngine.Trend trend;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit;
        public double riskRewardRatio;
        public double confidence;
        public String signalReason;

        // Subsequent outcome tracking
        public boolean outcomeResolved = false;
        public boolean outcomeHitTP = false;
        public boolean outcomeHitSL = false;
        public int candlesToOutcome = 0;
        public double priceChangePips = 0.0;

        @Override
        public String toString() {
            return "HistoricalSignalRecord{" +
                    "idx=" + candleIndex +
                    ", type=" + signalType +
                    ", entry=" + String.format(Locale.US, "%.2f", entryPrice) +
                    ", SL=" + String.format(Locale.US, "%.2f", stopLoss) +
                    ", TP=" + String.format(Locale.US, "%.2f", takeProfit) +
                    ", R:R=" + String.format(Locale.US, "%.2f", riskRewardRatio) +
                    ", confidence=" + String.format(Locale.US, "%.1f%%", confidence) +
                    ", resolved=" + outcomeResolved +
                    ", hitTP=" + outcomeHitTP +
                    '}';
        }
    }

    public static class HistoricalAnalysisResult {
        public int totalCandlesEvaluated = 0;
        public int totalSignalsGenerated = 0; // Total non-HOLD signals
        public int buyCount = 0;
        public int sellCount = 0;
        public int holdCount = 0;

        public double averageConfidence = 0.0;
        public int resolvedTradeCount = 0;
        public int successfulTradesCount = 0; // Hit TP
        public int failedTradesCount = 0;     // Hit SL

        public double historicalWinRate = 0.0;

        public List<HistoricalSignalRecord> signalHistory = new ArrayList<>();
        public BacktestEngine.BacktestResult backtestResult;
        public String arabicSummary = "";

        @Override
        public String toString() {
            return "HistoricalAnalysisResult{" +
                    "totalCandles=" + totalCandlesEvaluated +
                    ", buy=" + buyCount +
                    ", sell=" + sellCount +
                    ", hold=" + holdCount +
                    ", winRate=" + String.format(Locale.US, "%.1f%%", historicalWinRate * 100) +
                    ", avgConf=" + String.format(Locale.US, "%.1f%%", averageConfidence) +
                    '}';
        }
    }

    private SignalEngine signalEngine;

    public HistoricalAnalysisEngine() {
        this.signalEngine = new SignalEngine();
    }

    public HistoricalAnalysisEngine(SignalEngine signalEngine) {
        this.signalEngine = signalEngine != null ? signalEngine : new SignalEngine();
    }

    /**
     * Evaluates historical signals across the provided bars strictly with ZERO look-ahead bias.
     */
    public HistoricalAnalysisResult analyzeHistory(List<MarketIntelligenceEngine.Bar> bars) {
        return analyzeHistory(bars, null);
    }

    /**
     * Evaluates historical signals across the provided bars strictly with ZERO look-ahead bias,
     * and optionally integrates full backtest metrics if SharedPreferences is provided.
     */
    public HistoricalAnalysisResult analyzeHistory(List<MarketIntelligenceEngine.Bar> bars, SharedPreferences prefs) {
        HistoricalAnalysisResult result = new HistoricalAnalysisResult();

        if (bars == null || bars.size() < 30) {
            result.arabicSummary = "عدد البيانات غير كافٍ للتحليل التاريخي (يتطلب 30 شمعة على الأقل).";
            return result;
        }

        double totalConfSum = 0.0;
        int evaluatedCount = 0;

        // Iterate sequentially from candle index 29 (30th candle) to prevent look-ahead bias
        for (int i = 29; i < bars.size(); i++) {
            evaluatedCount++;
            SignalEngine.SignalResult sigRes = signalEngine.generateSignalAtCandle(bars, i);

            if (sigRes == null) continue;

            totalConfSum += sigRes.confidence;

            HistoricalSignalRecord record = new HistoricalSignalRecord();
            record.candleIndex = i;
            record.signalType = sigRes.signalType;
            record.trend = sigRes.trend;
            record.entryPrice = sigRes.entryPrice;
            record.stopLoss = sigRes.suggestedStopLoss;
            record.takeProfit = sigRes.suggestedTakeProfit;
            record.riskRewardRatio = sigRes.suggestedRiskReward;
            record.confidence = sigRes.confidence;
            record.signalReason = sigRes.signalReason;

            if (sigRes.signalType == SignalEngine.SignalType.BUY) {
                result.buyCount++;
                result.totalSignalsGenerated++;
                evaluateSubsequentOutcome(bars, i, record);
                result.signalHistory.add(record);
            } else if (sigRes.signalType == SignalEngine.SignalType.SELL) {
                result.sellCount++;
                result.totalSignalsGenerated++;
                evaluateSubsequentOutcome(bars, i, record);
                result.signalHistory.add(record);
            } else {
                result.holdCount++;
            }

            if (record.outcomeResolved) {
                result.resolvedTradeCount++;
                if (record.outcomeHitTP) {
                    result.successfulTradesCount++;
                } else if (record.outcomeHitSL) {
                    result.failedTradesCount++;
                }
            }
        }

        result.totalCandlesEvaluated = evaluatedCount;
        result.averageConfidence = evaluatedCount > 0 ? totalConfSum / evaluatedCount : 0.0;
        result.historicalWinRate = result.resolvedTradeCount > 0 ? (double) result.successfulTradesCount / result.resolvedTradeCount : 0.0;

        if (prefs != null) {
            result.backtestResult = BacktestEngine.runTradingDecisionBacktest(bars, prefs);
        }

        result.arabicSummary = generateArabicSummary(result);
        return result;
    }

    /**
     * Evaluates subsequent price movements strictly AFTER the signal candle `signalIndex`
     * to test signal outcome without look-ahead bias during signal generation.
     */
    private void evaluateSubsequentOutcome(List<MarketIntelligenceEngine.Bar> bars, int signalIndex, HistoricalSignalRecord record) {
        if (record.signalType == SignalEngine.SignalType.HOLD || record.stopLoss <= 0 || record.takeProfit <= 0) {
            return;
        }

        for (int j = signalIndex + 1; j < bars.size(); j++) {
            MarketIntelligenceEngine.Bar futureBar = bars.get(j);

            if (record.signalType == SignalEngine.SignalType.BUY) {
                if (futureBar.high >= record.takeProfit) {
                    record.outcomeResolved = true;
                    record.outcomeHitTP = true;
                    record.candlesToOutcome = j - signalIndex;
                    record.priceChangePips = (record.takeProfit - record.entryPrice) * 10;
                    break;
                } else if (futureBar.low <= record.stopLoss) {
                    record.outcomeResolved = true;
                    record.outcomeHitSL = true;
                    record.candlesToOutcome = j - signalIndex;
                    record.priceChangePips = (record.stopLoss - record.entryPrice) * 10;
                    break;
                }
            } else if (record.signalType == SignalEngine.SignalType.SELL) {
                if (futureBar.low <= record.takeProfit) {
                    record.outcomeResolved = true;
                    record.outcomeHitTP = true;
                    record.candlesToOutcome = j - signalIndex;
                    record.priceChangePips = (record.entryPrice - record.takeProfit) * 10;
                    break;
                } else if (futureBar.high >= record.stopLoss) {
                    record.outcomeResolved = true;
                    record.outcomeHitSL = true;
                    record.candlesToOutcome = j - signalIndex;
                    record.priceChangePips = (record.entryPrice - record.stopLoss) * 10;
                    break;
                }
            }
        }
    }

    private String generateArabicSummary(HistoricalAnalysisResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **ملخص نتائج التحليل التاريخي للإشارات (Historical Signal Summary)**\n");
        sb.append("• إجمالي الشموع المحللة: ").append(res.totalCandlesEvaluated).append("\n");
        sb.append("• إجمالي الإشارات الصادرة: ").append(res.totalSignalsGenerated).append(" (شراء: ").append(res.buyCount)
                .append(" | بيع: ").append(res.sellCount).append(" | انتظار: ").append(res.holdCount).append(")\n");
        sb.append("• متوسط نسبة الثقة للإشارات: ").append(String.format(Locale.US, "%.1f%%", res.averageConfidence)).append("\n");
        sb.append("• الصفقات المكتملة تاريخياً: ").append(res.resolvedTradeCount).append("\n");
        sb.append("• الصفقات الناجحة (TP): ").append(res.successfulTradesCount).append(" | الصفقات الخاسرة (SL): ").append(res.failedTradesCount).append("\n");
        sb.append("• نسبة النجاح التاريخية (Win Rate): ").append(String.format(Locale.US, "%.1f%%", res.historicalWinRate * 100)).append("\n");

        if (res.backtestResult != null) {
            sb.append("\n📈 **نتائج Backtesting الحسابية:**\n");
            sb.append("• إجمالي الصفقات: ").append(res.backtestResult.totalTrades).append("\n");
            sb.append("• Win Rate المحفظة: ").append(String.format(Locale.US, "%.1f%%", res.backtestResult.winRate * 100)).append("\n");
            sb.append("• Profit Factor: ").append(String.format(Locale.US, "%.2f", res.backtestResult.profitFactor)).append("\n");
            sb.append("• أقصى تراجع (Max Drawdown): ").append(String.format(Locale.US, "%.1f%%", res.backtestResult.maxDrawdown * 100)).append("\n");
            sb.append("• صافي الأرباح/الخسائر PnL: $").append(String.format(Locale.US, "%+.2f", res.backtestResult.netPnl)).append("\n");
        }

        return sb.toString();
    }
}
