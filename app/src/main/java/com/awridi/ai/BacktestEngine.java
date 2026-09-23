package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.List;

public class BacktestEngine {

    public static class BacktestResult {
        public int totalTrades;
        public int winningTrades;
        public int losingTrades;
        public double winRate, lossRate;
        public double grossProfit, grossLoss, netProfit, profitFactor, maxDrawdown;
        public double avgWin, avgLoss;
        public double largestWin, largestLoss;
        public int longestLosingStreak;
        public double startCapital;
        public double finalCapital;
    }

    public static BacktestResult runGoldBacktest(List<GoldAnalysisEngine.Bar> bars, SharedPreferences prefs) {
        BacktestResult bt = new BacktestResult();
        double startCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        double cash = startCap, peak = cash;
        int wins = 0, losses = 0;
        int currentLossStreak = 0, maxLossStreak = 0;

        bt.startCapital = startCap;

        for (int i = 50; i < bars.size() - 1; i++) {
            double rsi = GoldAnalysisEngine.calcRSI(bars, 14, i);
            double ema20 = GoldAnalysisEngine.calcEMA(bars, 20, i);
            double ema50 = GoldAnalysisEngine.calcEMA(bars, 50, i);
            double macdHist = GoldAnalysisEngine.calcMACDHist(bars, i);
            double atr = GoldAnalysisEngine.calcATR(bars, 14, i);
            GoldAnalysisEngine.Bar bar = bars.get(i);

            if (bar.c > ema50 && rsi >= 45 && rsi <= 68 && macdHist > 0 && ema20 > ema50) {
                // Buy Trade Setup
                bt.totalTrades++;
                double entry = bar.c;
                double sl = entry - (atr * 1.5);
                double tp = entry + (atr * 2.0);

                for (int j = i + 1; j < bars.size(); j++) {
                    GoldAnalysisEngine.Bar futureBar = bars.get(j);
                    if (futureBar.h >= tp) {
                        wins++;
                        double pnl = atr * 2.0 * 10;
                        cash += pnl;
                        bt.grossProfit += pnl;
                        if (pnl > bt.largestWin) bt.largestWin = pnl;
                        currentLossStreak = 0;
                        i = j;
                        break;
                    } else if (futureBar.l <= sl) {
                        losses++;
                        double pnl = atr * 1.5 * 10;
                        cash -= pnl;
                        bt.grossLoss += pnl;
                        if (pnl > bt.largestLoss) bt.largestLoss = pnl;
                        currentLossStreak++;
                        if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                        i = j;
                        break;
                    }
                }
            }
            peak = Math.max(peak, cash);
            double dd = peak > 0 ? (peak - cash) / peak : 0;
            if (dd > bt.maxDrawdown) bt.maxDrawdown = dd;
        }

        bt.winningTrades = wins;
        bt.losingTrades = losses;
        bt.finalCapital = cash;
        bt.netProfit = cash - startCap;
        bt.winRate = bt.totalTrades > 0 ? (double) wins / bt.totalTrades : 0;
        bt.lossRate = bt.totalTrades > 0 ? (double) losses / bt.totalTrades : 0;
        bt.profitFactor = bt.grossLoss > 0 ? bt.grossProfit / bt.grossLoss : (bt.grossProfit > 0 ? 99.0 : 0);
        bt.avgWin = wins > 0 ? bt.grossProfit / wins : 0;
        bt.avgLoss = losses > 0 ? bt.grossLoss / losses : 0;
        bt.longestLosingStreak = maxLossStreak;

        return bt;
    }
}
