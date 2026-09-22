package com.awridi.ai;

public class MarketIntelligenceResult {
    public double currentPrice;
    public String marketBias; // صعودي قوي / هابط قوي / محايد
    public String marketRegime; // اتجاه صاعد / اتجاه هابط / نطاق عرضي / تقلب مرتفع
    public String trend;
    public String momentum;
    public String volatility;
    public String htfStatus;

    // Historical Similarity Metrics
    public double similarityScore; // 0 to 100%
    public double bullishPct; // 0 to 100%
    public double bearishPct; // 0 to 100%
    public double avgForwardMove; // Average movement in USD ($)
    public int sampleCount; // Number of matching historical patterns found

    // Intelligence Score & Dynamic Decision
    public double intelligenceScore; // Dynamic score 0 to 100 calculated from real factors
    public String riskLevel; // منخفض / متوسط / مرتفع / حاد
    public String finalSignal; // BUY SETUP / SELL SETUP / WAIT / NO TRADE

    public String arabicExplanation;
    public String sentimentStatus = "التحليل غير متاح - يقتصر النظام على البيانات الفنية والتاريخية الفعلية للذهب بدون أخبار وهمية";
}
