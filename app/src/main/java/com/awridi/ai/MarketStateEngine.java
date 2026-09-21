package com.awridi.ai;

import java.util.List;

/**
 * Market State Engine responsible for classifying market regimes based on price structure,
 * trend strength, volatility, and momentum across multi-timeframe features.
 */
public class MarketStateEngine {

    public enum Regime {
        TREND_UP,
        TREND_DOWN,
        RANGE,
        HIGH_VOLATILITY,
        LOW_VOLATILITY,
        BREAKOUT,
        PULLBACK,
        REVERSAL,
        UNCERTAIN
    }

    public static class MarketState {
        public Regime primaryRegime;
        public String trendDescription;
        public String volatilityDescription;
        public String momentumDescription;
        public boolean isHigherHighs;
        public boolean isLowerLows;
        public double adxStrength;

        public MarketState(Regime primaryRegime, String trendDescription, String volatilityDescription, String momentumDescription, boolean isHigherHighs, boolean isLowerLows, double adxStrength) {
            this.primaryRegime = primaryRegime;
            this.trendDescription = trendDescription;
            this.volatilityDescription = volatilityDescription;
            this.momentumDescription = momentumDescription;
            this.isHigherHighs = isHigherHighs;
            this.isLowerLows = isLowerLows;
            this.adxStrength = adxStrength;
        }
    }

    public static MarketState evaluateMarketState(List<GoldAnalysisEngine.Bar> bars, FeatureEngine.FeatureVector features) {
        if (bars == null || bars.isEmpty() || features == null) {
            return new MarketState(Regime.UNCERTAIN, "غير محدد", "منخفض", "محايد", false, false, 0);
        }

        int n = bars.size();
        GoldAnalysisEngine.Bar latest = bars.get(n - 1);

        // Price structure check across last 5 bars
        boolean isHigherHighs = true;
        boolean isLowerLows = true;
        if (n >= 5) {
            for (int i = n - 4; i < n; i++) {
                if (bars.get(i).h <= bars.get(i - 1).h) isHigherHighs = false;
                if (bars.get(i).l >= bars.get(i - 1).l) isLowerLows = false;
            }
        } else {
            isHigherHighs = false;
            isLowerLows = false;
        }

        // Volatility classification
        String volDesc = features.atr14 > 4.0 ? "مرتفع جدًا (High)" : features.atr14 > 2.0 ? "متوسط (Medium)" : "منخفض (Low)";

        // Momentum classification
        String momDesc = features.macdHist > 0 && features.rsi > 50 ? "إيجابي قوي (Bullish)"
                       : features.macdHist < 0 && features.rsi < 50 ? "سلبي قوي (Bearish)"
                       : "متذبذب (Neutral)";

        // Regime Decision Tree
        Regime regime = Regime.UNCERTAIN;
        String trendDesc = "غير محدد";

        if (features.atr14 > 5.0 || features.volatility > 6.0) {
            regime = Regime.HIGH_VOLATILITY;
            trendDesc = "تقلبات حادة في الأسعار";
        } else if (features.adx > 25.0 && features.ema20 > features.ema50 && features.ema50 > features.ema200 && latest.c > features.ema20) {
            regime = Regime.TREND_UP;
            trendDesc = "اتجاه صاعد قوي (Strong Trend Up)";
        } else if (features.adx > 25.0 && features.ema20 < features.ema50 && features.ema50 < features.ema200 && latest.c < features.ema20) {
            regime = Regime.TREND_DOWN;
            trendDesc = "اتجاه هابط قوي (Strong Trend Down)";
        } else if (features.adx < 20.0 && Math.abs(features.rsi - 50.0) < 8.0) {
            regime = Regime.RANGE;
            trendDesc = "تداول عرضي / نطاق ضيق (Ranging)";
        } else if (features.ema20 > features.ema50 && latest.c < features.ema20 && latest.c > features.ema50) {
            regime = Regime.PULLBACK;
            trendDesc = "تراجع تصحيحي في اتجاه صاعد (Bullish Pullback)";
        } else if (features.ema20 < features.ema50 && latest.c > features.ema20 && latest.c < features.ema50) {
            regime = Regime.PULLBACK;
            trendDesc = "ارتداد تصحيحي في اتجاه هابط (Bearish Pullback)";
        } else if (latest.c > features.bbUpper) {
            regime = Regime.BREAKOUT;
            trendDesc = "اختراق للأعلى (Upper Breakout)";
        } else if (latest.c < features.bbLower) {
            regime = Regime.BREAKOUT;
            trendDesc = "كسر للأسفل (Lower Breakout)";
        } else {
            regime = Regime.UNCERTAIN;
            trendDesc = "سياق غير واضح / انتظار";
        }

        return new MarketState(regime, trendDesc, volDesc, momDesc, isHigherHighs, isLowerLows, features.adx);
    }
}
