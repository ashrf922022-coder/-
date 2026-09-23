package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketRegimeResult {

    public enum Regime {
        TREND_UP,
        TREND_DOWN,
        RANGE,
        HIGH_VOLATILITY,
        LOW_VOLATILITY,
        TRANSITION,
        UNCERTAIN
    }

    public Regime regime = Regime.UNCERTAIN;
    public double confidence = 0.0; // 0.0 to 100.0
    public List<String> supportingFactors = new ArrayList<>();
    public List<String> conflictingFactors = new ArrayList<>();
    public String explanation = "";

    // Technical Context Breakdown
    public double currentPrice = 0.0;
    public double ema20 = 0.0;
    public double ema50 = 0.0;
    public double ema200 = 0.0;
    public double atr14 = 0.0;
    public double volatilityChange = 0.0;
    public double rsi = 50.0;
    public double macdHist = 0.0;
    public double support = 0.0;
    public double resistance = 0.0;
    public String priceStructure = "غير محدد";

    @Override
    public String toString() {
        return "MarketRegimeResult{" +
                "regime=" + regime +
                ", confidence=" + String.format(Locale.US, "%.1f%%", confidence) +
                ", supportingFactors=" + supportingFactors.size() +
                ", conflictingFactors=" + conflictingFactors.size() +
                ", currentPrice=" + currentPrice +
                ", atr14=" + atr14 +
                '}';
    }
}
