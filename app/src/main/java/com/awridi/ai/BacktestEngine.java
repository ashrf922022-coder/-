package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced Backtesting Engine supporting multi-regime analysis, Long/Short setups,
 * Expectancy calculation, and Sharpe/Sortino ratios.
 */
public class BacktestEngine {

    public static class BacktestResult {
        public int totalTrades;
        public int winningTrades;
        public int losingTrades;
        public double winRate;
        public double lossRate;
        public double grossProfit;
        public double grossLoss;
        public double netProfit;
        public double profitFactor;
        public double maxDrawdown;
        public double avgWin;
        public double avgLoss;
        public double expectancy; // Average return per dollar risked
        public double sharpeRatio;
        public double sortinoRatio;
        public int longestWinningStreak;
        public int longestLosingStreak;
        public double finalCapital;
        public Map<MarketStateEngine.Regime, Integer> regimeTradeCounts = new HashMap<>();
    }

    public static BacktestResult runGoldBacktest(List<GoldAnalysisEngine.Bar> bars, SharedPreferences prefs) {
        BacktestResult bt = new BacktestResult();
        double startCap = 10000;
        if (prefs != null) {
            startCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        }
        double cash = startCap;
        double peak = cash;

        int currentWinStreak = 0;
        int maxWinStreak = 0;
        int currentLossStreak = 0;
        int maxLossStreak = 0;

        List<Double> returnsList = new ArrayList<>();

        for (int i = 50; i < bars.size() - 1; i++) {
            FeatureEngine.FeatureVector features = FeatureEngine.extractFeatures(bars, i);
            MarketStateEngine.MarketState state = MarketStateEngine.evaluateMarketState(bars.subList(0, i + 1), features);

            GoldAnalysisEngine.Bar bar = bars.get(i);
            boolean isBuy = bar.c > features.ema50 && features.rsi >= 45 && features.rsi <= 68 && features.macdHist > 0 && features.ema20 > features.ema50;
            boolean isSell = bar.c < features.ema50 && features.rsi <= 55 && features.rsi >= 32 && features.macdHist < 0 && features.ema20 < features.ema50;

            if (isBuy || isSell) {
                bt.totalTrades++;
                bt.regimeTradeCounts.put(state.primaryRegime, bt.regimeTradeCounts.getOrDefault(state.primaryRegime, 0) + 1);

                double entry = bar.c;
                double atr = Math.max(1.0, features.atr14);
                double sl = isBuy ? entry - (atr * 1.5) : entry + (atr * 1.5);
                double tp = isBuy ? entry + (atr * 2.0) : entry - (atr * 2.0);

                for (int j = i + 1; j < bars.size(); j++) {
                    GoldAnalysisEngine.Bar futureBar = bars.get(j);
                    boolean winHit = isBuy ? futureBar.h >= tp : futureBar.l <= tp;
                    boolean slHit = isBuy ? futureBar.l <= sl : futureBar.h >= sl;

                    if (winHit) {
                        bt.winningTrades++;
                        double pnl = atr * 2.0 * 10;
                        cash += pnl;
                        bt.grossProfit += pnl;
                        returnsList.add(pnl / startCap);

                        currentWinStreak++;
                        if (currentWinStreak > maxWinStreak) maxWinStreak = currentWinStreak;
                        currentLossStreak = 0;
                        i = j;
                        break;
                    } else if (slHit) {
                        bt.losingTrades++;
                        double pnl = atr * 1.5 * 10;
                        cash -= pnl;
                        bt.grossLoss += pnl;
                        returnsList.add(-pnl / startCap);

                        currentLossStreak++;
                        if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                        currentWinStreak = 0;
                        i = j;
                        break;
                    }
                }
            }
            peak = Math.max(peak, cash);
            double dd = (peak - cash) / peak;
            if (dd > bt.maxDrawdown) bt.maxDrawdown = dd;
        }

        bt.finalCapital = cash;
        bt.netProfit = bt.grossProfit - bt.grossLoss;
        bt.winRate = bt.totalTrades > 0 ? (double) bt.winningTrades / bt.totalTrades : 0;
        bt.lossRate = bt.totalTrades > 0 ? (double) bt.losingTrades / bt.totalTrades : 0;
        bt.profitFactor = bt.grossLoss > 0 ? bt.grossProfit / bt.grossLoss : (bt.grossProfit > 0 ? 99.0 : 0);
        bt.avgWin = bt.winningTrades > 0 ? bt.grossProfit / bt.winningTrades : 0;
        bt.avgLoss = bt.losingTrades > 0 ? bt.grossLoss / bt.losingTrades : 0;
        bt.longestWinningStreak = maxWinStreak;
        bt.longestLosingStreak = maxLossStreak;

        // Expectancy = (WinRate * AvgWin) - (LossRate * AvgLoss)
        bt.expectancy = (bt.winRate * bt.avgWin) - (bt.lossRate * bt.avgLoss);

        // Sharpe and Sortino Ratios
        if (!returnsList.isEmpty()) {
            double sumReturn = 0;
            for (double r : returnsList) sumReturn += r;
            double meanReturn = sumReturn / returnsList.size();

            double varSum = 0;
            double downsideVarSum = 0;
            for (double r : returnsList) {
                double diff = r - meanReturn;
                varSum += diff * diff;
                if (r < 0) {
                    downsideVarSum += r * r;
                }
            }
            double stdev = Math.sqrt(varSum / returnsList.size());
            double downsideStdev = Math.sqrt(downsideVarSum / returnsList.size());

            bt.sharpeRatio = stdev > 0 ? (meanReturn / stdev) * Math.sqrt(252) : 0;
            bt.sortinoRatio = downsideStdev > 0 ? (meanReturn / downsideStdev) * Math.sqrt(252) : 0;
        }

        return bt;
    }
}
