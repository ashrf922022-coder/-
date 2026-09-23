package com.awridi.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class BacktestAndMarketIntelligenceTest {

    @Test
    public void testMarketIntelligenceEngineAnalysis() {
        MarketIntelligenceEngine engine = new MarketIntelligenceEngine();
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();

        double price = 2600.0;
        for (int i = 0; i < 100; i++) {
            price += (i % 2 == 0 ? 1.5 : 0.5);
            bars.add(new MarketIntelligenceEngine.Bar(
                    price - 1.0,
                    price + 2.0,
                    price - 2.0,
                    price,
                    1000 + i * 10
            ));
        }

        MarketIntelligenceEngine.Result result = engine.analyze(bars);

        assertNotNull("Result should not be null", result);
        assertTrue("Current price should be positive", result.currentPrice > 0);
        assertNotNull("Trend should not be null", result.trend);
        assertNotNull("Trend strength should not be null", result.trendStrength);
        assertNotNull("Momentum should not be null", result.momentum);
        assertNotNull("Volatility should not be null", result.volatility);
        assertNotNull("Market state should not be null", result.marketState);

        // Verify educational signal fields
        assertNotNull("Educational signal should not be null", result.educationalSignal);
        assertTrue("Confidence score should be between 0 and 1", result.confidenceScore >= 0.0 && result.confidenceScore <= 1.0);
        assertNotNull("Signal reason should not be null", result.signalReason);
        assertNotNull("Supporting indicators list should not be null", result.supportingIndicators);
        assertNotNull("Conflicting indicators list should not be null", result.conflictingIndicators);

        // Verify indicator evaluations
        assertTrue("RSI evaluation should contain RSI text", result.rsiEvaluation.contains("القراءة"));
        assertTrue("MACD evaluation should contain MACD text", result.macdEvaluation.contains("القراءة"));
        assertTrue("EMA evaluation should contain EMA text", result.emaEvaluation.contains("EMA20"));
        assertTrue("ATR evaluation should contain ATR text", result.atrEvaluation.contains("القراءة"));
    }

    @Test
    public void testBacktestEngineMetricsCalculation() {
        List<GoldAnalysisEngine.Bar> bars = new ArrayList<>();
        double price = 2650.0;

        for (int i = 0; i < 200; i++) {
            price += (i % 3 == 0) ? 3.0 : (i % 3 == 1) ? -1.0 : 0.5;
            bars.add(new GoldAnalysisEngine.Bar(
                    price - 1.0,
                    price + 3.0,
                    price - 2.0,
                    price,
                    1500
            ));
        }

        // Dummy SharedPreferences mock
        DummySharedPreferences prefs = new DummySharedPreferences();

        BacktestEngine.BacktestResult result = BacktestEngine.runGoldBacktest(bars, prefs);

        assertNotNull("BacktestResult should not be null", result);
        assertTrue("Final capital should be calculated", result.finalCapital > 0);
        assertTrue("Win rate should be between 0 and 1", result.winRate >= 0.0 && result.winRate <= 1.0);
        assertTrue("Loss rate should be between 0 and 1", result.lossRate >= 0.0 && result.lossRate <= 1.0);
        assertTrue("Profit factor should be non-negative", result.profitFactor >= 0.0);
        assertTrue("Max drawdown should be between 0 and 1", result.maxDrawdown >= 0.0 && result.maxDrawdown <= 1.0);
        assertTrue("Longest losing streak should be non-negative", result.longestLosingStreak >= 0);
    }

    @Test
    public void testGoldAnalysisEngineCalculations() {
        List<GoldAnalysisEngine.Bar> bars = new ArrayList<>();
        double price = 2650.0;
        for (int i = 0; i < 60; i++) {
            price += 1.0;
            bars.add(new GoldAnalysisEngine.Bar(price - 0.5, price + 1.0, price - 1.0, price, 1000));
        }

        Map<String, List<GoldAnalysisEngine.Bar>> mtfBars = new HashMap<>();
        mtfBars.put("15min", bars);

        DummySharedPreferences prefs = new DummySharedPreferences();
        GoldAnalysisEngine.AnalysisResult res = GoldAnalysisEngine.analyzeGold(mtfBars, prefs);

        assertNotNull("AnalysisResult should not be null", res);
        assertTrue("Current price should match latest bar", res.currentPrice > 0);
        assertNotNull("Signal should not be null", res.signal);
        assertTrue("RSI should be between 0 and 100", res.rsi >= 0.0 && res.rsi <= 100.0);
        assertTrue("ATR should be greater than 0", res.atr > 0);
        assertNotNull("Arabic explanation should not be null", res.arabicExplanation);
    }

    // Lightweight mock for SharedPreferences in JVM unit tests
    private static class DummySharedPreferences implements android.content.SharedPreferences {
        private final Map<String, String> map = new HashMap<>();

        public DummySharedPreferences() {
            map.put(MainActivity.PREF_KEY_CAPITAL, "10000");
            map.put(MainActivity.PREF_KEY_RISK_PCT, "1.0");
        }

        @Override public Map<String, ?> getAll() { return null; }
        @Override public String getString(String key, String defValue) { return map.getOrDefault(key, defValue); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return null; }
        @Override public int getInt(String key, int defValue) { return 0; }
        @Override public long getLong(String key, long defValue) { return 0L; }
        @Override public float getFloat(String key, float defValue) { return 0f; }
        @Override public boolean getBoolean(String key, boolean defValue) { return false; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return null; }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }
}
