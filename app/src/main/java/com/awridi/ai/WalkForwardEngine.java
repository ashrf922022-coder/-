package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 8: Walk-Forward Validation Engine
 * Separate module for rolling-window Walk-Forward Analysis without Look-Ahead Bias.
 */
public class WalkForwardEngine {

    public static class WalkForwardWindow {
        public int windowIndex;
        public int inSampleStartBar;
        public int inSampleEndBar;
        public int outOfSampleStartBar;
        public int outOfSampleEndBar;

        public BacktestEngine.BacktestResult inSampleResult;
        public BacktestEngine.BacktestResult outOfSampleResult;

        @Override
        public String toString() {
            return "Window #" + windowIndex +
                    " [IS PnL=$" + (inSampleResult != null ? String.format(Locale.US, "%.2f", inSampleResult.netPnl) : "0") +
                    " | OOS PnL=$" + (outOfSampleResult != null ? String.format(Locale.US, "%.2f", outOfSampleResult.netPnl) : "0") + "]";
        }
    }

    public static class WalkForwardResult {
        public int totalWindows = 0;
        public double overallInSampleNetPnl = 0.0;
        public double overallOutOfSampleNetPnl = 0.0;
        public int totalOutOfSampleTrades = 0;
        public int winningOutOfSampleTrades = 0;
        public double outOfSampleWinRate = 0.0;
        public double walkForwardEfficiencyRatio = 0.0; // OOS Net Profit / IS Net Profit ratio

        public List<WalkForwardWindow> windows = new ArrayList<>();
        public String arabicSummary = "";

        @Override
        public String toString() {
            return "WalkForwardResult{" +
                    "windows=" + totalWindows +
                    ", IS PnL=$" + String.format(Locale.US, "%.2f", overallInSampleNetPnl) +
                    ", OOS PnL=$" + String.format(Locale.US, "%.2f", overallOutOfSampleNetPnl) +
                    ", Efficiency=" + String.format(Locale.US, "%.2f", walkForwardEfficiencyRatio) +
                    '}';
        }
    }

    /**
     * Executes Walk-Forward analysis over rolling windows.
     * @param bars Full historical bar dataset.
     * @param numWindows Number of rolling walk-forward windows (e.g. 3 to 5).
     * @param inSampleRatio Fraction of each window used for In-Sample (e.g. 0.7 = 70%).
     * @param params Base backtest parameters.
     */
    public static WalkForwardResult runWalkForward(List<MarketIntelligenceEngine.Bar> bars, int numWindows, double inSampleRatio, BacktestEngine.BacktestParams params) {
        WalkForwardResult result = new WalkForwardResult();

        if (bars == null || bars.size() < 60) {
            result.arabicSummary = "عدد الشموع غير كافٍ لتشغيل اختبار Walk-Forward (تتطلب 60 شمعة على الأقل).";
            return result;
        }

        int nWindows = Math.max(2, Math.min(10, numWindows));
        double isRatio = Math.max(0.5, Math.min(0.8, inSampleRatio));

        int windowSize = bars.size() / nWindows;
        if (windowSize < 30) {
            nWindows = Math.max(1, bars.size() / 30);
            windowSize = bars.size() / nWindows;
        }

        result.totalWindows = nWindows;
        int totalOosWins = 0;

        for (int w = 0; w < nWindows; w++) {
            int wStart = w * windowSize;
            int wEnd = (w == nWindows - 1) ? bars.size() : (w + 1) * windowSize;

            List<MarketIntelligenceEngine.Bar> windowBars = new ArrayList<>(bars.subList(wStart, wEnd));
            if (windowBars.size() < 30) continue;

            int isEnd = (int) Math.round(windowBars.size() * isRatio);
            isEnd = Math.max(20, Math.min(windowBars.size() - 10, isEnd));

            List<MarketIntelligenceEngine.Bar> isBars = new ArrayList<>(windowBars.subList(0, isEnd));
            List<MarketIntelligenceEngine.Bar> oosBars = new ArrayList<>(windowBars.subList(isEnd, windowBars.size()));

            WalkForwardWindow window = new WalkForwardWindow();
            window.windowIndex = w + 1;
            window.inSampleStartBar = wStart;
            window.inSampleEndBar = wStart + isEnd - 1;
            window.outOfSampleStartBar = wStart + isEnd;
            window.outOfSampleEndBar = wEnd - 1;

            // In-Sample Run
            BacktestEngine.BacktestParams isParams = cloneParams(params);
            isParams.inSampleRatio = 1.0;
            window.inSampleResult = BacktestEngine.runBacktest(isBars, isParams);

            // Out-of-Sample Run
            BacktestEngine.BacktestParams oosParams = cloneParams(params);
            oosParams.inSampleRatio = 1.0;
            window.outOfSampleResult = BacktestEngine.runBacktest(oosBars, oosParams);

            if (window.inSampleResult != null) {
                result.overallInSampleNetPnl += window.inSampleResult.netPnl;
            }
            if (window.outOfSampleResult != null) {
                result.overallOutOfSampleNetPnl += window.outOfSampleResult.netPnl;
                result.totalOutOfSampleTrades += window.outOfSampleResult.totalTrades;
                totalOosWins += window.outOfSampleResult.winningTrades;
            }

            result.windows.add(window);
        }

        result.winningOutOfSampleTrades = totalOosWins;
        result.outOfSampleWinRate = result.totalOutOfSampleTrades > 0 ? (double) totalOosWins / result.totalOutOfSampleTrades : 0.0;
        result.walkForwardEfficiencyRatio = result.overallInSampleNetPnl != 0.0 ?
                result.overallOutOfSampleNetPnl / Math.abs(result.overallInSampleNetPnl) : 0.0;

        result.arabicSummary = generateArabicSummary(result);
        return result;
    }

    private static BacktestEngine.BacktestParams cloneParams(BacktestEngine.BacktestParams orig) {
        BacktestEngine.BacktestParams copy = new BacktestEngine.BacktestParams();
        if (orig == null) return copy;
        copy.symbol = orig.symbol;
        copy.timeframe = orig.timeframe;
        copy.initialCapital = orig.initialCapital;
        copy.riskPerTradePct = orig.riskPerTradePct;
        copy.customPositionSizeLot = orig.customPositionSizeLot;
        copy.stopLossAtrMultiplier = orig.stopLossAtrMultiplier;
        copy.takeProfitAtrMultiplier = orig.takeProfitAtrMultiplier;
        copy.commissionPerLot = orig.commissionPerLot;
        copy.spreadPips = orig.spreadPips;
        copy.slippagePips = orig.slippagePips;
        return copy;
    }

    private static String generateArabicSummary(WalkForwardResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 **نتائج تحليل Walk-Forward Validation**\n");
        sb.append("• عدد الفترات المتنقلة (Windows): ").append(res.totalWindows).append("\n");
        sb.append("• إجمالي أرباح العينة الداخلة (In-Sample PnL): $").append(String.format(Locale.US, "%+.2f", res.overallInSampleNetPnl)).append("\n");
        sb.append("• إجمالي أرباح العينة الخارجة (Out-of-Sample PnL): $").append(String.format(Locale.US, "%+.2f", res.overallOutOfSampleNetPnl)).append("\n");
        sb.append("• صفقات العينة الخارجة: ").append(res.totalOutOfSampleTrades).append(" (الرابحة: ").append(res.winningOutOfSampleTrades).append(")\n");
        sb.append("• نسبة النجاح خارج العينة (OOS Win Rate): ").append(String.format(Locale.US, "%.1f%%", res.outOfSampleWinRate * 100)).append("\n");
        sb.append("• معامل كفاءة الاستراتيجية (Walk-Forward Efficiency): ").append(String.format(Locale.US, "%.2f", res.walkForwardEfficiencyRatio)).append("\n");
        return sb.toString();
    }
}
