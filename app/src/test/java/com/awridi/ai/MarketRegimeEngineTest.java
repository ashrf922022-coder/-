package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MarketRegimeEngineTest {

    private MarketRegimeEngine regimeEngine;

    @Before
    public void setUp() {
        regimeEngine = new MarketRegimeEngine();
    }

    private List<MarketIntelligenceEngine.Bar> createBullishBars(int count, double startPrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double current = startPrice;
        for (int i = 0; i < count; i++) {
            double delta = (i % 2 == 0) ? 0.35 : -0.15;
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
            double delta = (i % 2 == 0) ? -0.35 : 0.15;
            double open = current;
            double close = current + delta;
            double high = Math.max(open, close) + 0.1;
            double low = Math.min(open, close) - 0.1;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 1000.0));
            current = close;
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createRangeBars(int count, double basePrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double delta = (i % 2 == 0) ? 1.20 : -1.20;
            double open = basePrice;
            double close = basePrice + delta;
            double high = Math.max(open, close) + 0.8;
            double low = Math.min(open, close) - 0.8;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 800.0));
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createHighVolatilityBars(int count, double startPrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double current = startPrice;
        for (int i = 0; i < count; i++) {
            double open = current;
            double close = current + (i % 2 == 0 ? 3.0 : -2.5);
            double high = Math.max(open, close) + 6.0;
            double low = Math.min(open, close) - 6.0;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 5000.0));
            current = close;
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createLowVolatilityBars(int count, double basePrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double delta = (i % 2 == 0) ? 0.05 : -0.05;
            double open = basePrice;
            double close = basePrice + delta;
            double high = Math.max(open, close) + 0.05;
            double low = Math.min(open, close) - 0.05;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 300.0));
        }
        return bars;
    }

    // 1. Clear Upward Trend
    @Test
    public void test1_ClearUpwardTrend() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertEquals("Regime should be TREND_UP", MarketRegimeResult.Regime.TREND_UP, result.regime);
        assertTrue("Confidence should be >= 60%", result.confidence >= 60.0);
        assertFalse("Supporting factors should not be empty", result.supportingFactors.isEmpty());
        assertTrue("Explanation should contain TREND_UP", result.explanation.contains("TREND_UP"));
    }

    // 2. Clear Downward Trend
    @Test
    public void test2_ClearDownwardTrend() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertEquals("Regime should be TREND_DOWN", MarketRegimeResult.Regime.TREND_DOWN, result.regime);
        assertTrue("Confidence should be >= 60%", result.confidence >= 60.0);
        assertFalse("Supporting factors should not be empty", result.supportingFactors.isEmpty());
        assertTrue("Explanation should contain TREND_DOWN", result.explanation.contains("TREND_DOWN"));
    }

    // 3. Ranging Market
    @Test
    public void test3_RangeMarket() {
        List<MarketIntelligenceEngine.Bar> bars = createRangeBars(60, 2650.0);
        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertEquals("Regime should be RANGE", MarketRegimeResult.Regime.RANGE, result.regime);
        assertTrue("Confidence for range should be >= 70%", result.confidence >= 70.0);
        assertTrue("Supporting factors should mention range/support/resistance", result.supportingFactors.toString().contains("نطاق عرضي"));
    }

    // 4. High Volatility Market
    @Test
    public void test4_HighVolatilityMarket() {
        List<MarketIntelligenceEngine.Bar> bars = createHighVolatilityBars(60, 2650.0);
        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertEquals("Regime should be HIGH_VOLATILITY", MarketRegimeResult.Regime.HIGH_VOLATILITY, result.regime);
        assertTrue("ATR14 should be >= 4.5", result.atr14 >= 4.5);
        assertTrue("Confidence should be high for detected volatility", result.confidence >= 75.0);
        assertFalse("Conflicting factors should note volatility risks", result.conflictingFactors.isEmpty());
    }

    // 5. Low Volatility Market
    @Test
    public void test5_LowVolatilityMarket() {
        List<MarketIntelligenceEngine.Bar> bars = createLowVolatilityBars(60, 2650.0);
        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertEquals("Regime should be LOW_VOLATILITY", MarketRegimeResult.Regime.LOW_VOLATILITY, result.regime);
        assertTrue("ATR14 should be < 1.5", result.atr14 < 1.5);
        assertTrue("Confidence should be >= 70%", result.confidence >= 70.0);
    }

    // 6. Transition / Uncertain State
    @Test
    public void test6_TransitionUncertainState() {
        // Build 50 bullish bars then 5 sharp drop bars (price stays above EMA200 but EMAs cross & MACD negative)
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(50, 2600.0);
        double lastClose = bars.get(bars.size() - 1).close;
        for (int i = 0; i < 5; i++) {
            double open = lastClose;
            double close = lastClose - 1.5;
            bars.add(new MarketIntelligenceEngine.Bar(open, open + 0.2, close - 0.2, close, 1200.0));
            lastClose = close;
        }

        MarketRegimeResult result = regimeEngine.evaluate(bars);

        assertTrue("Regime should be TRANSITION or UNCERTAIN",
                result.regime == MarketRegimeResult.Regime.TRANSITION || result.regime == MarketRegimeResult.Regime.UNCERTAIN);
        assertFalse("Conflicting factors should contain warnings", result.conflictingFactors.isEmpty());
    }

    // 7. Missing Data Handling
    @Test
    public void test7_MissingDataHandling() {
        MarketRegimeResult nullRes = regimeEngine.evaluate(null);
        assertEquals("Null input yields UNCERTAIN regime", MarketRegimeResult.Regime.UNCERTAIN, nullRes.regime);
        assertEquals("Confidence should be 0.0 for missing data", 0.0, nullRes.confidence, 0.001);

        List<MarketIntelligenceEngine.Bar> shortBars = createBullishBars(15, 2600.0);
        MarketRegimeResult shortRes = regimeEngine.evaluate(shortBars);
        assertEquals("Fewer than 30 bars yields UNCERTAIN regime", MarketRegimeResult.Regime.UNCERTAIN, shortRes.regime);
        assertEquals("Confidence should be 0.0 for insufficient bars", 0.0, shortRes.confidence, 0.001);
        assertTrue("Conflicting factors should note insufficient data", shortRes.conflictingFactors.toString().contains("غير كافية"));
    }

    // 8. Invalid Data / Index Out-of-Bounds Handling
    @Test
    public void test8_InvalidDataAndIndexOutOfBounds() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(50, 2600.0);

        MarketRegimeResult outOfBoundsHigh = regimeEngine.evaluateAtCandle(bars, 100);
        assertEquals("Out-of-bounds high index yields UNCERTAIN regime", MarketRegimeResult.Regime.UNCERTAIN, outOfBoundsHigh.regime);
        assertEquals("Confidence should be 0.0 for invalid index", 0.0, outOfBoundsHigh.confidence, 0.001);

        MarketRegimeResult outOfBoundsNeg = regimeEngine.evaluateAtCandle(bars, -5);
        assertEquals("Negative index yields UNCERTAIN regime", MarketRegimeResult.Regime.UNCERTAIN, outOfBoundsNeg.regime);
        assertEquals("Confidence should be 0.0 for invalid index", 0.0, outOfBoundsNeg.confidence, 0.001);
    }

    // 9. Consistency / Determinism Test
    @Test
    public void test9_ConsistencyAndDeterminism() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);

        MarketRegimeResult res1 = regimeEngine.evaluate(bars);
        MarketRegimeResult res2 = regimeEngine.evaluate(bars);

        assertEquals("Regime must be identical", res1.regime, res2.regime);
        assertEquals("Confidence must be identical", res1.confidence, res2.confidence, 0.00001);
        assertEquals("Supporting factors count must match", res1.supportingFactors.size(), res2.supportingFactors.size());
        assertEquals("Current price must be identical", res1.currentPrice, res2.currentPrice, 0.00001);
        assertEquals("ATR14 must be identical", res1.atr14, res2.atr14, 0.00001);
        assertEquals("Explanation text must match", res1.explanation, res2.explanation);
    }

    // 10. No Look-Ahead Bias Test
    @Test
    public void test10_NoLookAheadBiasVerification() {
        List<MarketIntelligenceEngine.Bar> fullSeries = createBullishBars(70, 2600.0);

        // Evaluate slice at target index 35
        MarketRegimeResult resAt35Original = regimeEngine.evaluateAtCandle(fullSeries, 35);

        // Mutate future bars from index 36 to 69 with extreme price collapse
        List<MarketIntelligenceEngine.Bar> mutatedSeries = new ArrayList<>();
        for (int i = 0; i <= 35; i++) {
            mutatedSeries.add(fullSeries.get(i));
        }
        double crashPrice = fullSeries.get(35).close;
        for (int i = 36; i < 70; i++) {
            crashPrice -= 15.0;
            mutatedSeries.add(new MarketIntelligenceEngine.Bar(crashPrice + 5, crashPrice + 10, crashPrice - 5, crashPrice, 8000));
        }

        // Evaluate mutated series at the SAME target index 35
        MarketRegimeResult resAt35Mutated = regimeEngine.evaluateAtCandle(mutatedSeries, 35);

        assertNotNull("Original result at 35 must not be null", resAt35Original);
        assertNotNull("Mutated result at 35 must not be null", resAt35Mutated);

        assertEquals("Candle 35 current price must remain identical", resAt35Original.currentPrice, resAt35Mutated.currentPrice, 0.0001);
        assertEquals("Candle 35 Market Regime must remain identical", resAt35Original.regime, resAt35Mutated.regime);
        assertEquals("Candle 35 Confidence must remain identical", resAt35Original.confidence, resAt35Mutated.confidence, 0.0001);
        assertEquals("Candle 35 ATR14 must remain identical", resAt35Original.atr14, resAt35Mutated.atr14, 0.0001);
        assertEquals("Candle 35 Supporting factors count must remain identical", resAt35Original.supportingFactors.size(), resAt35Mutated.supportingFactors.size());
    }
}
