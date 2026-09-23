package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;

public class TradingDecisionResult {

    public enum Decision {
        BUY,
        SELL,
        WAIT,
        NO_TRADE
    }

    public enum Direction {
        BULLISH,
        BEARISH,
        NEUTRAL,
        UNKNOWN
    }

    public enum SignalQuality {
        HIGH,
        MEDIUM,
        LOW,
        INVALID
    }

    public String symbol = "XAU/USD";
    public Direction direction = Direction.NEUTRAL;
    public Decision decision = Decision.NO_TRADE;
    public long timestamp = System.currentTimeMillis();

    public String trend = "غير محدد";
    public String trendStrength = "غير محدد";
    public String momentum = "غير محدد";
    public String volatility = "غير محدد";
    public double atrValue = 0.0;
    public double volatilityChange = 0.0; // Percentage change in volatility if available
    public String priceStructure = "غير محدد";

    public double currentPrice = 0.0;
    public double support = 0.0;
    public double resistance = 0.0;
    public double ema20 = 0.0;
    public double ema50 = 0.0;
    public double ema200 = 0.0;
    public double rsi = 50.0;
    public double macdHist = 0.0;

    // Phase 2 Signal Scoring fields
    public double bullishScore = 0.0;
    public double bearishScore = 0.0;
    public double totalScore = 0.0;
    public double confidencePct = 0.0; // 0.0% to 100.0%
    public SignalQuality signalQuality = SignalQuality.INVALID;

    public List<String> supportingFactors = new ArrayList<>();
    public List<String> conflictingFactors = new ArrayList<>();
    public String arabicExplanation = "";

    @Override
    public String toString() {
        return "TradingDecisionResult{" +
                "symbol='" + symbol + '\'' +
                ", direction=" + direction +
                ", decision=" + decision +
                ", timestamp=" + timestamp +
                ", trend='" + trend + '\'' +
                ", momentum='" + momentum + '\'' +
                ", volatility='" + volatility + '\'' +
                ", priceStructure='" + priceStructure + '\'' +
                ", bullishScore=" + bullishScore +
                ", bearishScore=" + bearishScore +
                ", totalScore=" + totalScore +
                ", confidencePct=" + confidencePct +
                ", signalQuality=" + signalQuality +
                ", supportingFactors=" + supportingFactors.size() +
                ", conflictingFactors=" + conflictingFactors.size() +
                '}';
    }
}
