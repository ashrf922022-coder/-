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

    public int bullishScore = 0;
    public int bearishScore = 0;
    public int totalScore = 0;
    public double confidence = 0.0; // Confidence % (0.0 to 100.0)
    public String signalQuality = "INVALID"; // "HIGH", "MEDIUM", "LOW", "INVALID"

    public MarketRegimeResult marketRegime;

    public List<String> supportingFactors = new ArrayList<>();
    public List<String> conflictingFactors = new ArrayList<>();
    public String arabicExplanation = "";

    @Override
    public String toString() {
        return "TradingDecisionResult{" +
                "symbol='" + symbol + '\'' +
                ", direction=" + direction +
                ", decision=" + decision +
                ", signalQuality='" + signalQuality + '\'' +
                ", confidence=" + String.format(java.util.Locale.US, "%.1f%%", confidence) +
                ", bullishScore=" + bullishScore +
                ", bearishScore=" + bearishScore +
                ", totalScore=" + totalScore +
                ", timestamp=" + timestamp +
                ", trend='" + trend + '\'' +
                ", momentum='" + momentum + '\'' +
                ", volatility='" + volatility + '\'' +
                ", priceStructure='" + priceStructure + '\'' +
                ", marketRegime=" + (marketRegime != null ? marketRegime.regime : "NULL") +
                ", supportingFactors=" + supportingFactors.size() +
                ", conflictingFactors=" + conflictingFactors.size() +
                '}';
    }
}
