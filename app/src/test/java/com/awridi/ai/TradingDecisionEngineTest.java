package com.awridi.ai;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TradingDecisionEngineTest {

    private List<MarketIntelligenceEngine.Bar> createBullishBars(int count, double startPrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double current = startPrice;
        for (int i = 0; i < count; i++) {
            // Net positive trend with gain 0.3 and pullback 0.15 -> RSI ~ 66.6, MACD > 0
            double delta = (i % 2 == 0) ? 0.3 : -0.15;
            double open = current;
            double close = current + delta;
            double high = Math.max(open, close) + 0.1;
            double low = Math.min(open, close) - 0.1;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 1000.0));
            current = close;
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createBearishBars(int count, double startPrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double current = startPrice;
        for (int i = 0; i < count; i++) {
            // Net negative trend with loss 0.3 and bounce 0.15 -> RSI ~ 33.3, MACD < 0
            double delta = (i % 2 == 0) ? -0.3 : 0.15;
            double open = current;
            double close = current + delta;
            double high = Math.max(open, close) + 0.1;
            double low = Math.min(open, close) - 0.1;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 1000.0));
            current = close;
        }
        return bars;
    }

    @Test
    public void test1_ClearBuyDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertEquals("Decision should be BUY for clear bullish alignment", TradingDecisionResult.Decision.BUY, result.decision);
        assertEquals("Direction should be BULLISH", TradingDecisionResult.Direction.BULLISH, result.direction);
        assertFalse("Supporting factors should not be empty", result.supportingFactors.isEmpty());
    }

    @Test
    public void test2_ClearSellDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertEquals("Decision should be SELL for clear bearish alignment", TradingDecisionResult.Decision.SELL, result.decision);
        assertEquals("Direction should be BEARISH", TradingDecisionResult.Direction.BEARISH, result.direction);
        assertFalse("Conflicting/bearish factors should be populated", result.conflictingFactors.isEmpty());
    }

    @Test
    public void test3_WaitDueToConflictingIndicators() {
        // Bullish baseline followed by sharp price drop -> EMAs bullish but price below EMA50 & MACD negative
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(40, 2600.0);
        double lastClose = bars.get(bars.size() - 1).close;
        for (int i = 0; i < 20; i++) {
            double open = lastClose;
            double close = lastClose - 2.5;
            bars.add(new MarketIntelligenceEngine.Bar(open, open + 0.5, close - 0.5, close, 1000.0));
            lastClose = close;
        }

        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertEquals("Decision should be WAIT due to conflicting signals", TradingDecisionResult.Decision.WAIT, result.decision);
        assertEquals("Direction should be NEUTRAL", TradingDecisionResult.Direction.NEUTRAL, result.direction);
    }

    @Test
    public void test4_NoTradeDueToInsufficientData() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(15, 2650.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertEquals("Decision should be NO_TRADE when bars < 30", TradingDecisionResult.Decision.NO_TRADE, result.decision);
        assertEquals("Direction should be UNKNOWN", TradingDecisionResult.Direction.UNKNOWN, result.direction);
        assertTrue("Arabic explanation should mention insufficient data", result.arabicExplanation.contains("غير كافية"));
    }

    @Test
    public void test5_BullishTrendDetection() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("Trend should contain صاعد", result.trend.contains("صاعد"));
        assertTrue("EMA20 > EMA50", result.ema20 > result.ema50);
    }

    @Test
    public void test6_BearishTrendDetection() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("Trend should contain هابط", result.trend.contains("هابط"));
        assertTrue("EMA20 < EMA50", result.ema20 < result.ema50);
    }

    @Test
    public void test7_BullishMomentumDetection() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("MACD Histogram should be positive for bullish momentum", result.macdHist > 0);
    }

    @Test
    public void test8_BearishMomentumDetection() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("MACD Histogram should be negative for bearish momentum", result.macdHist < 0);
    }

    @Test
    public void test9_HighVolatilityHandling() {
        // Create bars with massive wicks -> high ATR
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2650.0;
        for (int i = 0; i < 60; i++) {
            bars.add(new MarketIntelligenceEngine.Bar(price, price + 15.0, price - 15.0, price + 1.0, 1000));
            price += 1.0;
        }

        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("ATR value should be high (> 4.5)", result.atrValue >= 4.5);
        assertEquals("Extreme high volatility should yield NO_TRADE decision", TradingDecisionResult.Decision.NO_TRADE, result.decision);
        assertTrue("Conflicting factors should note high volatility", result.arabicExplanation.contains("تقلب"));
    }

    @Test
    public void test10_HistoricalDataWithoutLookAheadBias() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();

        TradingDecisionResult resAt35 = engine.evaluateAtCandle(bars, 35);
        TradingDecisionResult resAt59 = engine.evaluateAtCandle(bars, 59);

        assertNotNull("Result at candle 35 should not be null", resAt35);
        assertNotNull("Result at candle 59 should not be null", resAt59);
        assertTrue("Price at candle 35 must be lower than at candle 59", resAt35.currentPrice < resAt59.currentPrice);
        assertEquals("Evaluation at candle 35 should match bar 35 close", bars.get(35).close, resAt35.currentPrice, 0.001);
    }

    @Test
    public void test11_DecisionExplanationAndFactors() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(50, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult result = engine.evaluate(bars);

        assertNotNull("Arabic explanation should not be null", result.arabicExplanation);
        assertTrue("Explanation should mention final decision", result.arabicExplanation.contains("القرار النهائي"));
        assertNotNull("Supporting factors list must be initialized", result.supportingFactors);
        assertNotNull("Conflicting factors list must be initialized", result.conflictingFactors);
    }

    @Test
    public void test12_MissingOrNullDataHandling() {
        TradingDecisionEngine engine = new TradingDecisionEngine();

        TradingDecisionResult nullResult = engine.evaluate(null);
        assertEquals("Null input should yield NO_TRADE", TradingDecisionResult.Decision.NO_TRADE, nullResult.decision);
        assertEquals("Direction should be UNKNOWN", TradingDecisionResult.Direction.UNKNOWN, nullResult.direction);

        TradingDecisionResult invalidIndexRes = engine.evaluateAtCandle(createBullishBars(50, 2600.0), 100);
        assertEquals("Out of bounds index should yield NO_TRADE", TradingDecisionResult.Decision.NO_TRADE, invalidIndexRes.decision);
    }

    @Test
    public void test13_BacktestEngineIntegrationWithTradingDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(70, 2600.0);
        DummySharedPreferences prefs = new DummySharedPreferences();

        BacktestEngine.BacktestResult btRes = BacktestEngine.runTradingDecisionBacktest(bars, prefs);
        assertNotNull("Backtest result should not be null", btRes);
        assertTrue("Total trades executed should be >= 0", btRes.totalTrades >= 0);
    }

    private static class DummySharedPreferences implements android.content.SharedPreferences {
        private final Map<String, String> map = new HashMap<>();

        public DummySharedPreferences() {
            map.put(MainActivity.PREF_KEY_CAPITAL, "10000");
            map.put(MainActivity.PREF_KEY_RISK_PCT, "1.0");
        }

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String defValue) { return map.getOrDefault(key, defValue); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return null; }
        @Override public int getInt(String key, int defValue) { return Integer.parseInt(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public long getLong(String key, long defValue) { return Long.parseLong(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public float getFloat(String key, float defValue) { return Float.parseFloat(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public boolean getBoolean(String key, boolean defValue) { return Boolean.parseBoolean(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return null; }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }
}
