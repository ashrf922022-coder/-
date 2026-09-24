package com.awridi.ai;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AIDecisionEngineTest {

    private AIDecisionEngine aiEngine;
    private MockSharedPreferences mockPrefs;

    @Before
    public void setUp() {
        aiEngine = new AIDecisionEngine();
        mockPrefs = new MockSharedPreferences();
        mockPrefs.edit()
                .putString(MainActivity.PREF_KEY_CAPITAL, "10000.0")
                .putString(MainActivity.PREF_KEY_RISK_PCT, "1.0")
                .putString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0")
                .apply();
        KillSwitch.deactivate(mockPrefs);
    }

    private List<MarketIntelligenceEngine.Bar> createUptrendBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2500.0;
        for (int i = 0; i < count; i++) {
            double change = (i % 2 == 0) ? 1.0 : -0.6;
            price += change;
            double open = price - change;
            double high = Math.max(open, price) + 0.5;
            double low = Math.min(open, price) - 0.5;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, price, 1500));
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createDowntrendBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2700.0;
        for (int i = 0; i < count; i++) {
            double change = (i % 2 == 0) ? -1.0 : 0.6;
            price += change;
            double open = price - change;
            double high = Math.max(open, price) + 0.5;
            double low = Math.min(open, price) - 0.5;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, price, 1500));
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createSidewaysBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double basePrice = 2650.0;
        for (int i = 0; i < count; i++) {
            bars.add(new MarketIntelligenceEngine.Bar(basePrice, basePrice + 0.5, basePrice - 0.5, basePrice, 1000));
        }
        return bars;
    }

    @Test
    public void test1_BUYDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res);
        assertEquals("XAU/USD", res.symbol);
        assertEquals(AIDecisionResult.Decision.BUY, res.decision);
        assertTrue(res.riskApproved);
        assertTrue(res.confidence > 0.0);
    }

    @Test
    public void test2_SELLDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createDowntrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res);
        assertEquals(AIDecisionResult.Decision.SELL, res.decision);
        assertTrue(res.riskApproved);
        assertTrue(res.confidence > 0.0);
    }

    @Test
    public void test3_WAITDecision() {
        List<MarketIntelligenceEngine.Bar> bars = createSidewaysBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res);
        assertEquals(AIDecisionResult.Decision.WAIT, res.decision);
    }

    @Test
    public void test4_ConflictingSignals() {
        List<MarketIntelligenceEngine.Bar> bars = createSidewaysBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertEquals(AIDecisionResult.Decision.WAIT, res.decision);
    }

    @Test
    public void test5_StrongConfluence() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(80);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertTrue(res.confluenceScore > 0.0);
        assertNotNull(res.confluenceLevel);
    }

    @Test
    public void test6_WeakConfluence() {
        List<MarketIntelligenceEngine.Bar> bars = createSidewaysBars(40);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res.confluenceLevel);
    }

    @Test
    public void test7_MarketRegimeDetection() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res.marketRegime);
        assertNotNull(res.marketRegimeNameArabic);
        assertTrue(res.marketRegimeConfidence > 0);
    }

    @Test
    public void test8_ConfidenceCalculation() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertTrue(res.confidence >= 0.0 && res.confidence <= 100.0);
        assertNotNull(res.confidenceExplanation);
        assertTrue(res.confidenceExplanation.contains("ملاحظة: الثقة ليست احتمالية إحصائية معصومة للربح"));
    }

    @Test
    public void test9_TradeQualityCalculation() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertTrue(res.tradeQualityScore >= 0.0 && res.tradeQualityScore <= 100.0);
        assertNotNull(res.tradeQuality);
    }

    @Test
    public void test10_RiskRejection() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);

        PortfolioManager.PortfolioTrade lossTrade = new PortfolioManager.PortfolioTrade();
        lossTrade.id = "LOSS1";
        lossTrade.pnl = -400.0;
        lossTrade.status = "LOSS";
        lossTrade.date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        PortfolioManager.saveTrades(mockPrefs, java.util.Collections.singletonList(lossTrade));

        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertEquals(AIDecisionResult.Decision.WAIT, res.decision);
        assertFalse(res.riskApproved);
    }

    @Test
    public void test11_KillSwitch() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        KillSwitch.activate(mockPrefs);

        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertEquals(AIDecisionResult.Decision.WAIT, res.decision);
        assertFalse(res.riskApproved);
        assertTrue(res.rejectionReason.contains("Kill Switch"));
    }

    @Test
    public void test12_PositionSizeLimits() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertTrue(res.positionSizeLot >= 0.0);
        assertTrue(res.positionSizeLot <= 50.0);
    }

    @Test
    public void test13_DecisionExplanation() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        AIDecisionResult res = aiEngine.evaluate(bars, "XAU/USD", "15min", mockPrefs);

        assertNotNull(res.arabicExplanation);
        assertTrue(res.arabicExplanation.contains("قرار محرك الذكاء الاصطناعي"));
        assertFalse(res.decisionReasons.isEmpty());
    }

    @Test
    public void test14_DecisionHistory() {
        AIDecisionResult res = new AIDecisionResult();
        res.decisionId = "TEST_DEC_1";
        res.decision = AIDecisionResult.Decision.BUY;
        res.symbol = "XAU/USD";

        AIDecisionHistory.saveDecision(mockPrefs, res);

        List<AIDecisionResult> list = AIDecisionHistory.loadDecisionHistory(mockPrefs);
        assertFalse(list.isEmpty());
        assertEquals("TEST_DEC_1", list.get(0).decisionId);

        boolean deleted = AIDecisionHistory.deleteDecisionById(mockPrefs, "TEST_DEC_1");
        assertTrue(deleted);

        List<AIDecisionResult> emptyList = AIDecisionHistory.loadDecisionHistory(mockPrefs);
        assertTrue(emptyList.isEmpty());
    }

    @Test
    public void test15_LookAheadBiasProtection() {
        List<MarketIntelligenceEngine.Bar> fullBars = createUptrendBars(80);

        for (int i = 50; i < 80; i++) {
            MarketIntelligenceEngine.Bar b = fullBars.get(i);
            fullBars.set(i, new MarketIntelligenceEngine.Bar(b.open - 100, b.high - 100, b.low - 100, b.close - 100, 10000));
        }

        AIDecisionResult resAt45 = aiEngine.evaluateAtCandle(fullBars, 45, "XAU/USD", "15min", mockPrefs);

        assertNotNull(resAt45);
        assertEquals("XAU/USD", resAt45.symbol);
        assertEquals(AIDecisionResult.Decision.BUY, resAt45.decision);
    }

    @Test
    public void test16_BacktestingIntegration() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(100);
        BacktestEngine.BacktestParams params = new BacktestEngine.BacktestParams();
        params.symbol = "XAU/USD";
        params.initialCapital = 10000.0;

        BacktestEngine.BacktestResult btRes = BacktestEngine.runAIBacktest(bars, params, mockPrefs);

        assertNotNull(btRes);
        assertTrue(btRes.totalTrades >= 0);
        assertNotNull(btRes.arabicSummary);
    }

    @Test
    public void test17_RegressionTests() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);

        TradingDecisionEngine tdEngine = new TradingDecisionEngine();
        TradingDecisionResult tdRes = tdEngine.evaluate(bars);
        assertNotNull(tdRes);

        SignalEngine signalEngine = new SignalEngine();
        SignalEngine.SignalResult sigRes = signalEngine.generateSignal(bars);
        assertNotNull(sigRes);

        ExecutionEngine executionEngine = new ExecutionEngine();
        ExecutionOrder order = new ExecutionOrder(ExecutionOrder.Action.BUY, ExecutionOrder.OrderType.MARKET, 2650.0, 2645.0, 2660.0, 0.1);
        ExecutionOrder execRes = executionEngine.executeOrder(order, 10000.0, 0.0, mockPrefs);
        assertNotNull(execRes);
        assertEquals(ExecutionOrder.OrderStatus.FILLED, execRes.status);
    }

    private static class MockSharedPreferences implements SharedPreferences {
        private final java.util.Map<String, Object> map = new java.util.HashMap<>();

        @Override
        public java.util.Map<String, ?> getAll() { return map; }

        @Override
        public String getString(String key, String defValue) {
            return map.containsKey(key) ? (String) map.get(key) : defValue;
        }

        @Override
        public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
            return (java.util.Set<String>) map.getOrDefault(key, defValues);
        }

        @Override
        public int getInt(String key, int defValue) {
            return map.containsKey(key) ? (Integer) map.get(key) : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            return map.containsKey(key) ? (Long) map.get(key) : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            return map.containsKey(key) ? (Float) map.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return map.containsKey(key) ? (Boolean) map.get(key) : defValue;
        }

        @Override
        public boolean contains(String key) { return map.containsKey(key); }

        @Override
        public Editor edit() { return new MockEditor(map); }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static class MockEditor implements Editor {
            private final java.util.Map<String, Object> map;

            MockEditor(java.util.Map<String, Object> map) { this.map = map; }

            @Override
            public Editor putString(String key, String value) { map.put(key, value); return this; }

            @Override
            public Editor putStringSet(String key, java.util.Set<String> values) { map.put(key, values); return this; }

            @Override
            public Editor putInt(String key, int value) { map.put(key, value); return this; }

            @Override
            public Editor putLong(String key, long value) { map.put(key, value); return this; }

            @Override
            public Editor putFloat(String key, float value) { map.put(key, value); return this; }

            @Override
            public Editor putBoolean(String key, boolean value) { map.put(key, value); return this; }

            @Override
            public Editor remove(String key) { map.remove(key); return this; }

            @Override
            public Editor clear() { map.clear(); return this; }

            @Override
            public boolean commit() { return true; }

            @Override
            public void apply() {}
        }
    }
}
