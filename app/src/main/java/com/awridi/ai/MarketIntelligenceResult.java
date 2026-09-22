package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;

public class MarketIntelligenceResult {
    public String symbol = "XAU/USD";
    public String timeframe = "15m";
    public int candleCount = 150;
    public double currentPrice;

    // Market Overview & Regime
    public String marketBias; // صعودي قوي / هابط قوي / محايد
    public String marketRegime; // اتجاه صاعد / اتجاه هابط / نطاق عرضي / تقلب مرتفع
    public String trend;
    public String trendStrength; // قوي / متوسط / ضعيف
    public String momentum;
    public String volatility;
    public String volumeAnalysis; // مرتفع / طبيعي / منخفض / غير متاح
    public String htfStatus;

    // Support, Resistance, Breakout & Pullback Detection
    public double supportLevel;
    public double resistanceLevel;
    public boolean isBreakoutDetected;
    public String breakoutDetails;
    public boolean isPullbackDetected;
    public String pullbackDetails;

    // Technical Indicators
    public double sma20;
    public double sma50;
    public double ema20;
    public double ema50;
    public double ema200;
    public double rsi;
    public double macdHist;
    public double atr;
    public double bbUpper;
    public double bbMiddle;
    public double bbLower;
    public double avgVolume;

    // Historical Period Statistics
    public double periodHigh;
    public double periodLow;
    public double periodChangePct;
    public double periodVolatilityUsd;

    // Historical Pattern Analysis
    public String patternType; // Continuation / Reversal / Breakout / Pullback / Range
    public String patternRationale;

    // Historical Similarity Metrics
    public double similarityScore; // 0 to 100%
    public double bullishPct; // 0 to 100%
    public double bearishPct; // 0 to 100%
    public double avgForwardMove; // USD ($)
    public int sampleCount; // Number of matching historical patterns

    // Market Score & Factor Factors Breakdown
    public double intelligenceScore; // 0 to 100
    public String riskLevel; // منخفض / متوسط / مرتفع / حاد
    public String finalSignal; // BUY SETUP / SELL SETUP / WAIT / NO TRADE
    public List<String> scoreFactors = new ArrayList<>();

    public String arabicExplanation;
    public String sentimentStatus = "تحليل الأخبار غير متاح حالياً - تعتمد النتائج على البيانات التاريخية والفنية الفعلية للذهب";
}
