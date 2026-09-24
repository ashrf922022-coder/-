package com.awridi.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Phase 8: Monte Carlo / Strategy Robustness Engine
 * Evaluates strategy robustness via trade sequence permutation and random slippage/spread variation.
 */
public class MonteCarloEngine {

    public static class MonteCarloResult {
        public int iterations = 100;
        public double initialCapital = 10000.0;
        public double originalNetPnl = 0.0;
        public double originalMaxDrawdownPct = 0.0;

        public double minFinalEquity = Double.MAX_VALUE;
        public double maxFinalEquity = Double.MIN_VALUE;
        public double meanFinalEquity = 0.0;
        public double percentile5FinalEquity = 0.0; // 95% Confidence Level Minimum Equity

        public double maxDrawdownPctObserved = 0.0;
        public double meanMaxDrawdownPct = 0.0;
        public double percentile95DrawdownPct = 0.0; // 95% Worst-Case Drawdown

        public double riskOfRuinPercentage = 0.0; // % of simulations with drawdown > 50% or blown account
        public String arabicSummary = "";

        @Override
        public String toString() {
            return "MonteCarloResult{" +
                    "iterations=" + iterations +
                    ", meanEquity=$" + String.format(Locale.US, "%.2f", meanFinalEquity) +
                    ", 95%MinEquity=$" + String.format(Locale.US, "%.2f", percentile5FinalEquity) +
                    ", 95%MaxDD=" + String.format(Locale.US, "%.2f%%", percentile95DrawdownPct) +
                    ", RiskOfRuin=" + String.format(Locale.US, "%.1f%%", riskOfRuinPercentage) +
                    '}';
        }
    }

    /**
     * Runs Monte Carlo simulation using trade permutation and slippage variation on a BacktestResult.
     */
    public static MonteCarloResult runMonteCarlo(BacktestEngine.BacktestResult baseResult, int iterations, long seed) {
        MonteCarloResult result = new MonteCarloResult();
        if (baseResult == null || baseResult.trades == null || baseResult.trades.isEmpty()) {
            result.arabicSummary = "لا توجد صفقات منفذة لتشغيل محاكاة مونتي كارلو.";
            return result;
        }

        int numSims = Math.max(10, Math.min(1000, iterations));
        result.iterations = numSims;
        result.initialCapital = baseResult.initialCapital;
        result.originalNetPnl = baseResult.netPnl;
        result.originalMaxDrawdownPct = baseResult.maxDrawdownPct;

        List<Double> tradePnls = new ArrayList<>();
        for (BacktestEngine.BacktestTrade t : baseResult.trades) {
            tradePnls.add(t.pnlUsd);
        }

        List<Double> finalEquities = new ArrayList<>();
        List<Double> maxDrawdowns = new ArrayList<>();
        int ruinCount = 0;

        Random rnd = new Random(seed > 0 ? seed : 12345);

        for (int sim = 0; sim < numSims; sim++) {
            List<Double> shuffledPnls = new ArrayList<>(tradePnls);
            Collections.shuffle(shuffledPnls, rnd);

            double cash = result.initialCapital;
            double peak = cash;
            double simMaxDDAmount = 0.0;
            double simMaxDDPct = 0.0;

            for (double pnl : shuffledPnls) {
                // Apply random slippage/spread variation (-$2 to +$2 per trade)
                double variation = (rnd.nextDouble() - 0.5) * 4.0;
                double adjustedPnl = pnl + variation;

                cash += adjustedPnl;
                if (cash > peak) {
                    peak = cash;
                } else {
                    double dd = peak - cash;
                    double ddPct = peak > 0 ? (dd / peak) * 100.0 : 0.0;
                    if (ddPct > simMaxDDPct) {
                        simMaxDDAmount = dd;
                        simMaxDDPct = ddPct;
                    }
                }
            }

            finalEquities.add(cash);
            maxDrawdowns.add(simMaxDDPct);

            if (simMaxDDPct >= 50.0 || cash <= result.initialCapital * 0.2) {
                ruinCount++;
            }
        }

        Collections.sort(finalEquities);
        Collections.sort(maxDrawdowns);

        double totalEquitySum = 0.0;
        for (double eq : finalEquities) totalEquitySum += eq;
        double totalDDSum = 0.0;
        for (double dd : maxDrawdowns) totalDDSum += dd;

        result.minFinalEquity = finalEquities.get(0);
        result.maxFinalEquity = finalEquities.get(finalEquities.size() - 1);
        result.meanFinalEquity = totalEquitySum / numSims;

        int idx5Pct = (int) Math.round(numSims * 0.05);
        idx5Pct = Math.max(0, Math.min(numSims - 1, idx5Pct));
        result.percentile5FinalEquity = finalEquities.get(idx5Pct);

        int idx95Pct = (int) Math.round(numSims * 0.95);
        idx95Pct = Math.max(0, Math.min(numSims - 1, idx95Pct));
        result.percentile95DrawdownPct = maxDrawdowns.get(idx95Pct);

        result.maxDrawdownPctObserved = maxDrawdowns.get(maxDrawdowns.size() - 1);
        result.meanMaxDrawdownPct = totalDDSum / numSims;
        result.riskOfRuinPercentage = ((double) ruinCount / numSims) * 100.0;

        result.arabicSummary = generateArabicSummary(result);
        return result;
    }

    private static String generateArabicSummary(MonteCarloResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎲 **نتائج محاكاة مونتي كارلو لاختبار متانة الاستراتيجية (Monte Carlo Robustness)**\n");
        sb.append("• عدد المحاكاة: ").append(res.iterations).append(" تكرار عشوائي\n");
        sb.append("• متوسط رأس المال النهائي المتوقع: $").append(String.format(Locale.US, "%.2f", res.meanFinalEquity)).append("\n");
        sb.append("• رأس المال النهائي عند درجة ثقة 95% (Worst 5%): $").append(String.format(Locale.US, "%.2f", res.percentile5FinalEquity)).append("\n");
        sb.append("• متوسط أقصى انخفاض متوقع (Mean Max DD): ").append(String.format(Locale.US, "%.2f%%", res.meanMaxDrawdownPct)).append("\n");
        sb.append("• أسوأ انخفاض متوقع عند درجة ثقة 95%: ").append(String.format(Locale.US, "%.2f%%", res.percentile95DrawdownPct)).append("\n");
        sb.append("• احتمالية التعثر أو الإفلاس (Risk of Ruin >50% DD): ").append(String.format(Locale.US, "%.1f%%", res.riskOfRuinPercentage)).append("\n");
        return sb.toString();
    }
}
