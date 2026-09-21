package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.List;

/**
 * Walk-Forward Testing Engine preventing overfitting through sliding-window
 * In-Sample (Training) vs Out-of-Sample (Validation) analysis.
 */
public class WalkForwardEngine {

    public static class WalkForwardReport {
        public final int totalWindows;
        public final double averageInSampleWinRate;
        public final double averageOutOfSampleWinRate;
        public final double winRateDegradation; // (InSample - OutOfSample) / InSample
        public final double outOfSampleProfitFactor;
        public final boolean isOverfitted;

        public WalkForwardReport(int totalWindows, double averageInSampleWinRate, double averageOutOfSampleWinRate,
                                 double winRateDegradation, double outOfSampleProfitFactor, boolean isOverfitted) {
            this.totalWindows = totalWindows;
            this.averageInSampleWinRate = averageInSampleWinRate;
            this.averageOutOfSampleWinRate = averageOutOfSampleWinRate;
            this.winRateDegradation = winRateDegradation;
            this.outOfSampleProfitFactor = outOfSampleProfitFactor;
            this.isOverfitted = isOverfitted;
        }
    }

    public static WalkForwardReport runWalkForwardAnalysis(List<GoldAnalysisEngine.Bar> bars, SharedPreferences prefs) {
        if (bars == null || bars.size() < 120) {
            return new WalkForwardReport(0, 0, 0, 0, 0, false);
        }

        int totalBars = bars.size();
        int windowSize = 60; // 60 bars per window
        int inSampleCount = 40; // 40 training bars
        int outOfSampleCount = 20; // 20 validation bars

        int step = 20;
        int windowsProcessed = 0;

        double sumInSampleWinRate = 0;
        double sumOutOfSampleWinRate = 0;
        double sumOutOfSampleProfit = 0;
        double sumOutOfSampleLoss = 0;

        for (int start = 0; start + windowSize <= totalBars; start += step) {
            List<GoldAnalysisEngine.Bar> inSampleBars = bars.subList(start, start + inSampleCount);
            List<GoldAnalysisEngine.Bar> outOfSampleBars = bars.subList(start + inSampleCount, start + windowSize);

            BacktestEngine.BacktestResult inSampleRes = BacktestEngine.runGoldBacktest(inSampleBars, prefs);
            BacktestEngine.BacktestResult outOfSampleRes = BacktestEngine.runGoldBacktest(outOfSampleBars, prefs);

            sumInSampleWinRate += inSampleRes.winRate;
            sumOutOfSampleWinRate += outOfSampleRes.winRate;

            sumOutOfSampleProfit += outOfSampleRes.grossProfit;
            sumOutOfSampleLoss += outOfSampleRes.grossLoss;

            windowsProcessed++;
        }

        if (windowsProcessed == 0) {
            return new WalkForwardReport(0, 0, 0, 0, 0, false);
        }

        double avgInSampleWR = sumInSampleWinRate / windowsProcessed;
        double avgOutOfSampleWR = sumOutOfSampleWinRate / windowsProcessed;
        double degradation = avgInSampleWR > 0 ? (avgInSampleWR - avgOutOfSampleWR) / avgInSampleWR : 0;
        double oosProfitFactor = sumOutOfSampleLoss > 0 ? sumOutOfSampleProfit / sumOutOfSampleLoss : (sumOutOfSampleProfit > 0 ? 99.0 : 0);

        boolean overfitted = degradation > 0.35 || (avgOutOfSampleWR < 0.40 && avgInSampleWR > 0.65);

        return new WalkForwardReport(windowsProcessed, avgInSampleWR, avgOutOfSampleWR, degradation, oosProfitFactor, overfitted);
    }
}
