package com.awridi.ai;

import java.util.List;

/**
 * Feature Engineering Engine responsible for extracting quantitative feature vectors
 * from raw OHLCV bars across multiple timeframes.
 */
public class FeatureEngine {

    public static class FeatureVector {
        public double rsi;
        public double macdLine;
        public double macdSignal;
        public double macdHist;
        public double ema9;
        public double ema20;
        public double ema50;
        public double ema200;
        public double sma20;
        public double atr14;
        public double bbUpper;
        public double bbMiddle;
        public double bbLower;
        public double bbWidth;
        public double bbPosition; // 0.0 at lower band, 1.0 at upper band
        public double adx;
        public double plusDI;
        public double minusDI;
        public double volatility; // Standard deviation of close prices
        public double candleBody;
        public double upperWick;
        public double lowerWick;
        public double candleRange;
        public double pctChange;
        public double momentum;
        public double volume;
    }

    public static FeatureVector extractFeatures(List<GoldAnalysisEngine.Bar> bars, int index) {
        if (bars == null || bars.isEmpty() || index < 0 || index >= bars.size()) {
            return new FeatureVector();
        }

        FeatureVector f = new FeatureVector();
        GoldAnalysisEngine.Bar cur = bars.get(index);

        // Raw Candle Structure
        f.candleRange = cur.h - cur.l;
        f.candleBody = Math.abs(cur.c - cur.o);
        f.upperWick = cur.h - Math.max(cur.o, cur.c);
        f.lowerWick = Math.min(cur.o, cur.c) - cur.l;
        f.volume = cur.v;

        if (index > 0) {
            GoldAnalysisEngine.Bar prev = bars.get(index - 1);
            f.pctChange = ((cur.c - prev.c) / Math.max(0.1, prev.c)) * 100.0;
            f.momentum = cur.c - prev.c;
        }

        // Indicators
        f.rsi = GoldAnalysisEngine.calcRSI(bars, 14, index);
        f.ema9 = GoldAnalysisEngine.calcEMA(bars, 9, index);
        f.ema20 = GoldAnalysisEngine.calcEMA(bars, 20, index);
        f.ema50 = GoldAnalysisEngine.calcEMA(bars, 50, index);
        f.ema200 = GoldAnalysisEngine.calcEMA(bars, 200, index);
        f.atr14 = GoldAnalysisEngine.calcATR(bars, 14, index);

        // SMA 20 & Bollinger Bands
        int bbPeriod = 20;
        int bbStart = Math.max(0, index - bbPeriod + 1);
        double sum = 0;
        int count = 0;
        for (int i = bbStart; i <= index; i++) {
            sum += bars.get(i).c;
            count++;
        }
        f.sma20 = count > 0 ? sum / count : cur.c;

        double varianceSum = 0;
        for (int i = bbStart; i <= index; i++) {
            double diff = bars.get(i).c - f.sma20;
            varianceSum += diff * diff;
        }
        f.volatility = Math.sqrt(varianceSum / Math.max(1, count));
        f.bbMiddle = f.sma20;
        f.bbUpper = f.bbMiddle + (2.0 * f.volatility);
        f.bbLower = f.bbMiddle - (2.0 * f.volatility);
        f.bbWidth = f.bbUpper - f.bbLower;
        double bbSpan = Math.max(0.001, f.bbUpper - f.bbLower);
        f.bbPosition = (cur.c - f.bbLower) / bbSpan;

        // MACD (12, 26, 9)
        f.macdHist = GoldAnalysisEngine.calcMACDHist(bars, index);
        double ema12 = GoldAnalysisEngine.calcEMA(bars, 12, index);
        double ema26 = GoldAnalysisEngine.calcEMA(bars, 26, index);
        f.macdLine = ema12 - ema26;
        f.macdSignal = f.macdLine - f.macdHist;

        // ADX & DMI
        calcADX(bars, index, 14, f);

        return f;
    }

    private static void calcADX(List<GoldAnalysisEngine.Bar> bars, int index, int period, FeatureVector f) {
        if (index < period) {
            f.adx = 20.0;
            f.plusDI = 20.0;
            f.minusDI = 20.0;
            return;
        }

        double plusDMSum = 0;
        double minusDMSum = 0;
        double trSum = 0;

        int start = Math.max(1, index - period + 1);
        for (int i = start; i <= index; i++) {
            GoldAnalysisEngine.Bar cur = bars.get(i);
            GoldAnalysisEngine.Bar prev = bars.get(i - 1);

            double upMove = cur.h - prev.h;
            double downMove = prev.l - cur.l;

            double plusDM = (upMove > downMove && upMove > 0) ? upMove : 0;
            double minusDM = (downMove > upMove && downMove > 0) ? downMove : 0;

            double tr = Math.max(cur.h - cur.l, Math.max(Math.abs(cur.h - prev.c), Math.abs(cur.l - prev.c)));

            plusDMSum += plusDM;
            minusDMSum += minusDM;
            trSum += tr;
        }

        trSum = Math.max(0.001, trSum);
        f.plusDI = (plusDMSum / trSum) * 100.0;
        f.minusDI = (minusDMSum / trSum) * 100.0;

        double diDiff = Math.abs(f.plusDI - f.minusDI);
        double diSum = Math.max(0.001, f.plusDI + f.minusDI);
        f.adx = (diDiff / diSum) * 100.0;
    }
}
