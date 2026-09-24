package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BacktestEngineTest {

    private List<MarketIntelligenceEngine.Bar> sampleBars;

    @Before
    public void setUp() {
        HistoricalDataProvider.HistoricalDataBatch batch =
                HistoricalDataProvider.generateMockBars("XAU/USD", "15min", 150, 2650.0, 1001);
        sampleBars = batch.bars;
    }

    @Test
    public void testBacktestEngineBasicExecution() {
        BacktestEngine.BacktestParams params = new BacktestEngine.BacktestParams();
        params.initialCapital = 10000.0;
        params.riskPerTradePct = 1.0;
        params.spreadPips = 0.20;
        params.slippagePips = 0.10;
        params.commissionPerLot = 2.0;

        BacktestEngine.BacktestResult result = BacktestEngine.runBacktest(sampleBars, params);

        assertNotNull("Result should not be null", result);
        assertEquals("Initial capital should match params", 10000.0, result.initialCapital, 0.001);
        assertTrue("Total trades should be non-negative", result.totalTrades >= 0);
        assertTrue("Win rate should be between 0 and 1", result.winRate >= 0.0 && result.winRate <= 1.0);
        assertTrue("Max drawdown percentage should be non-negative", result.maxDrawdownPct >= 0.0);
        assertNotNull("Equity curve should be created", result.equityCurve);
        assertFalse("Equity curve should contain points", result.equityCurve.isEmpty());
        assertNotNull("Drawdown analysis should be populated", result.drawdownAnalysis);
    }

    @Test
    public void testZeroLookAheadBiasProtection() {
        SignalEngine signalEngine = new SignalEngine();

        int targetIndex = 50;
        // 1. Evaluate signal at targetIndex using full bars list via generateSignalAtCandle
        SignalEngine.SignalResult fullListSignal = signalEngine.generateSignalAtCandle(sampleBars, targetIndex);

        // 2. Evaluate signal using sliced sublist strictly up to targetIndex
        List<MarketIntelligenceEngine.Bar> slicedBars = new ArrayList<>(sampleBars.subList(0, targetIndex + 1));
        SignalEngine.SignalResult slicedListSignal = signalEngine.generateSignal(slicedBars);

        assertNotNull(fullListSignal);
        assertNotNull(slicedListSignal);
        assertEquals("Signal type must be identical with sliced or full dataset up to candle",
                fullListSignal.signalType, slicedListSignal.signalType);
        assertEquals("Entry price must be identical", fullListSignal.entryPrice, slicedListSignal.entryPrice, 0.001);
        assertEquals("Confidence must be identical", fullListSignal.confidence, slicedListSignal.confidence, 0.001);
    }

    @Test
    public void testBuyAndSellExecutionFriction() {
        BacktestEngine.BacktestParams params = new BacktestEngine.BacktestParams();
        params.spreadPips = 0.50; // $0.50 spread
        params.slippagePips = 0.25; // $0.25 slippage
        params.commissionPerLot = 5.0; // $5 per lot commission

        BacktestEngine.BacktestResult result = BacktestEngine.runBacktest(sampleBars, params);

        if (!result.trades.isEmpty()) {
            BacktestEngine.BacktestTrade firstTrade = result.trades.get(0);
            assertTrue("Commission paid should be positive", firstTrade.commissionPaidUsd > 0);
            assertTrue("Spread and slippage cost should be positive", firstTrade.spreadSlippageCostUsd > 0);
        }
    }

    @Test
    public void testPositionSizingCustomVsAuto() {
        BacktestEngine.BacktestParams fixedParams = new BacktestEngine.BacktestParams();
        fixedParams.customPositionSizeLot = 0.5; // Fixed 0.5 Lot

        BacktestEngine.BacktestResult fixedResult = BacktestEngine.runBacktest(sampleBars, fixedParams);

        if (!fixedResult.trades.isEmpty()) {
            for (BacktestEngine.BacktestTrade t : fixedResult.trades) {
                assertEquals("Position size should equal custom lot size", 0.5, t.lotSize, 0.001);
            }
        }
    }

    @Test
    public void testEmptyAndInvalidDatasetHandling() {
        // Empty dataset
        BacktestEngine.BacktestResult emptyResult = BacktestEngine.runBacktest(new ArrayList<>(), new BacktestEngine.BacktestParams());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.totalTrades);

        // Null dataset
        BacktestEngine.BacktestResult nullResult = BacktestEngine.runBacktest(null, new BacktestEngine.BacktestParams());
        assertNotNull(nullResult);
        assertEquals(0, nullResult.totalTrades);

        // Short dataset (< 30 bars)
        List<MarketIntelligenceEngine.Bar> shortBars = sampleBars.subList(0, 15);
        BacktestEngine.BacktestResult shortResult = BacktestEngine.runBacktest(shortBars, new BacktestEngine.BacktestParams());
        assertNotNull(shortResult);
        assertEquals(0, shortResult.totalTrades);
    }

    @Test
    public void testExtremePriceMovementsAndSpikes() {
        List<MarketIntelligenceEngine.Bar> spikeBars = new ArrayList<>(sampleBars);
        // Introduce an extreme price spike at candle 60
        MarketIntelligenceEngine.Bar spikeBar = new MarketIntelligenceEngine.Bar(2650.0, 2800.0, 2500.0, 2750.0, 50000);
        spikeBars.set(60, spikeBar);

        BacktestEngine.BacktestResult result = BacktestEngine.runBacktest(spikeBars, new BacktestEngine.BacktestParams());
        assertNotNull(result);
        assertFalse(Double.isNaN(result.finalCapital));
        assertFalse(Double.isInfinite(result.finalCapital));
    }

    @Test
    public void testDuplicateAndMissingCandles() {
        List<MarketIntelligenceEngine.Bar> duplicateBars = new ArrayList<>(sampleBars);
        // Duplicate candle 40 three times
        duplicateBars.add(41, sampleBars.get(40));
        duplicateBars.add(42, sampleBars.get(40));

        BacktestEngine.BacktestResult result = BacktestEngine.runBacktest(duplicateBars, new BacktestEngine.BacktestParams());
        assertNotNull(result);
        assertTrue(result.finalCapital > 0);
    }

    @Test
    public void testDataSplittingInSampleAndOutOfSample() {
        List<List<MarketIntelligenceEngine.Bar>> split = BacktestEngine.splitData(sampleBars, 0.7);
        assertEquals(2, split.size());

        List<MarketIntelligenceEngine.Bar> inSample = split.get(0);
        List<MarketIntelligenceEngine.Bar> outOfSample = split.get(1);

        assertEquals(sampleBars.size(), inSample.size() + outOfSample.size());
        assertTrue(inSample.size() > outOfSample.size());
    }

    @Test
    public void testWalkForwardEngineExecution() {
        WalkForwardEngine.WalkForwardResult wfResult =
                WalkForwardEngine.runWalkForward(sampleBars, 3, 0.7, new BacktestEngine.BacktestParams());

        assertNotNull(wfResult);
        assertEquals(3, wfResult.totalWindows);
        assertFalse(wfResult.windows.isEmpty());
        assertNotNull(wfResult.arabicSummary);
    }

    @Test
    public void testMonteCarloEngineExecution() {
        BacktestEngine.BacktestResult btRes = BacktestEngine.runBacktest(sampleBars, new BacktestEngine.BacktestParams());

        MonteCarloEngine.MonteCarloResult mcResult = MonteCarloEngine.runMonteCarlo(btRes, 50, 42);

        assertNotNull(mcResult);
        assertEquals(50, mcResult.iterations);
        assertTrue(mcResult.meanFinalEquity > 0);
        assertTrue(mcResult.percentile95DrawdownPct >= 0);
        assertNotNull(mcResult.arabicSummary);
    }

    @Test
    public void testHistoricalDataProviderMockTagging() {
        HistoricalDataProvider.HistoricalDataBatch batch =
                HistoricalDataProvider.generateMockBars("XAU/USD", "15min", 50, 2650.0, 99);

        assertNotNull(batch);
        assertTrue(batch.isMock);
        assertTrue(batch.dataLabel.contains("MOCK DATA"));
        assertEquals(50, batch.bars.size());
    }

    @Test
    public void testBacktestSaveLoadAndComparison() {
        // Create mock prefs emulator
        MockSharedPreferences mockPrefs = new MockSharedPreferences();

        BacktestEngine.BacktestResult bt1 = BacktestEngine.runBacktest(sampleBars, new BacktestEngine.BacktestParams());
        bt1.runId = "BT_TEST_1";

        BacktestEngine.BacktestParams p2 = new BacktestEngine.BacktestParams();
        p2.riskPerTradePct = 2.0;
        BacktestEngine.BacktestResult bt2 = BacktestEngine.runBacktest(sampleBars, p2);
        bt2.runId = "BT_TEST_2";

        BacktestEngine.saveBacktestRun(mockPrefs, bt1);
        BacktestEngine.saveBacktestRun(mockPrefs, bt2);

        List<BacktestEngine.BacktestResult> loaded = BacktestEngine.loadBacktestRuns(mockPrefs);
        assertNotNull(loaded);
        assertTrue(loaded.size() >= 2);

        String comparisonTable = BacktestEngine.buildArabicComparisonTable(loaded);
        assertNotNull(comparisonTable);
        assertTrue(comparisonTable.contains("مقارنة الاختبارات التاريخية"));
    }

    // Simple Mock SharedPreferences implementation for unit testing
    private static class MockSharedPreferences implements android.content.SharedPreferences {
        private final java.util.Map<String, String> map = new java.util.HashMap<>();

        @Override public java.util.Map<String, ?> getAll() { return new java.util.HashMap<>(map); }
        @Override public String getString(String key, String defValue) { return map.getOrDefault(key, defValue); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return null; }
        @Override public int getInt(String key, int defValue) { return 0; }
        @Override public long getLong(String key, long defValue) { return 0; }
        @Override public float getFloat(String key, float defValue) { return 0; }
        @Override public boolean getBoolean(String key, boolean defValue) { return false; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return new MockEditor(map); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static class MockEditor implements Editor {
            private final java.util.Map<String, String> map;
            MockEditor(java.util.Map<String, String> map) { this.map = map; }
            @Override public Editor putString(String key, String value) { map.put(key, value); return this; }
            @Override public Editor putStringSet(String key, java.util.Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { return this; }
            @Override public Editor putLong(String key, long value) { return this; }
            @Override public Editor putFloat(String key, float value) { return this; }
            @Override public Editor putBoolean(String key, boolean value) { return this; }
            @Override public Editor remove(String key) { map.remove(key); return this; }
            @Override public Editor clear() { map.clear(); return this; }
            @Override public boolean commit() { return true; }
            @Override public void apply() {}
        }
    }
}
