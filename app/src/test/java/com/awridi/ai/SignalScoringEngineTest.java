package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SignalScoringEngineTest {

    private TradingDecisionEngine engine;

    @Before
    public void setUp() {
        engine = new TradingDecisionEngine();
    }

    private List<MarketIntelligenceEngine.Bar> createBullishBars(int count, double startPrice) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double current = startPrice;
        for (int i = 0; i < count; i++) {
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

    // 1. Clear Bullish Trend
    @Test
    public void test1_ClearBullishTrend() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("Trend should contain صاعد", result.trend.contains("صاعد"));
        assertEquals("Decision should be BUY for clear bullish trend", TradingDecisionResult.Decision.BUY, result.decision);
        assertTrue("Bullish score should be >= 4", result.bullishScore >= 4);
        assertTrue("Confidence % should be >= 75%", result.confidence >= 75.0);
        assertTrue("Signal Quality should be HIGH or MEDIUM", "HIGH".equals(result.signalQuality) || "MEDIUM".equals(result.signalQuality));
    }

    // 2. Clear Bearish Trend
    @Test
    public void test2_ClearBearishTrend() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("Trend should contain هابط", result.trend.contains("هابط"));
        assertEquals("Decision should be SELL for clear bearish trend", TradingDecisionResult.Decision.SELL, result.decision);
        assertTrue("Bearish score should be >= 4", result.bearishScore >= 4);
        assertTrue("Confidence % should be >= 75%", result.confidence >= 75.0);
        assertTrue("Signal Quality should be HIGH or MEDIUM", "HIGH".equals(result.signalQuality) || "MEDIUM".equals(result.signalQuality));
    }

    // 3. Alignment of Multiple Bullish Indicators
    @Test
    public void test3_MultipleBullishIndicatorsAlignment() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("EMA20 > EMA50", result.ema20 > result.ema50);
        assertTrue("Price > EMA200", result.currentPrice > result.ema200);
        assertTrue("MACD Histogram > 0", result.macdHist > 0);
        assertTrue("Bullish Score should exceed Bearish Score significantly", result.bullishScore > result.bearishScore + 2);
        assertFalse("Supporting factors should be non-empty", result.supportingFactors.isEmpty());
    }

    // 4. Alignment of Multiple Bearish Indicators
    @Test
    public void test4_MultipleBearishIndicatorsAlignment() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionResult result = engine.evaluate(bars);

        assertTrue("EMA20 < EMA50", result.ema20 < result.ema50);
        assertTrue("Price < EMA200", result.currentPrice < result.ema200);
        assertTrue("MACD Histogram < 0", result.macdHist < 0);
        assertTrue("Bearish Score should exceed Bullish Score significantly", result.bearishScore > result.bullishScore + 2);
        assertFalse("Conflicting/Bearish factors should be non-empty", result.conflictingFactors.isEmpty());
    }

    // 5. Indicator Conflict -> WAIT
    @Test
    public void test5_IndicatorConflictYieldsWait() {
        TradingDecisionResult dummyRes = new TradingDecisionResult();
        dummyRes.currentPrice = 2640.0; // Below EMA50 (+1 Bearish)
        dummyRes.ema20 = 2655.0; // Bullish alignment (EMA20 > EMA50) (+1 Bullish)
        dummyRes.ema50 = 2645.0;
        dummyRes.ema200 = 2630.0; // Price > EMA200 (+1 Bullish)
        dummyRes.macdHist = -0.15; // Negative MACD (+1 Bearish)
        dummyRes.rsi = 40.0; // Bearish RSI range (+1 Bearish)
        dummyRes.atrValue = 1.5;
        dummyRes.priceStructure = "حركة داخلية في النطاق";

        SignalScoringEngine.evaluateScore(dummyRes);

        assertEquals("Decision should be WAIT when indicators conflict", TradingDecisionResult.Decision.WAIT, dummyRes.decision);
        assertEquals("Direction should be NEUTRAL", TradingDecisionResult.Direction.NEUTRAL, dummyRes.direction);
        assertEquals("Bullish score should be 2", 2, dummyRes.bullishScore);
        assertEquals("Bearish score should be 3", 3, dummyRes.bearishScore);
    }

    // 6. Low Confidence Handling
    @Test
    public void test6_LowConfidenceDoesNotForceBuySell() {
        TradingDecisionResult dummyRes = new TradingDecisionResult();
        dummyRes.currentPrice = 2640.0; // Below EMA50 (+1 Bearish)
        dummyRes.ema20 = 2651.0; // EMA20 > EMA50 (+1 Bullish)
        dummyRes.ema50 = 2650.0;
        dummyRes.ema200 = 2630.0; // Price > EMA200 (+1 Bullish)
        dummyRes.rsi = 50.0; // Bullish RSI range (+1 Bullish)
        dummyRes.macdHist = -0.05; // Negative MACD (+1 Bearish)
        dummyRes.atrValue = 1.5;
        dummyRes.priceStructure = "حركة داخلية في النطاق";

        SignalScoringEngine.evaluateScore(dummyRes);

        assertEquals("Low confidence / conflict should yield WAIT decision", TradingDecisionResult.Decision.WAIT, dummyRes.decision);
        assertNotEquals("Low confidence / weak alignment should not yield HIGH quality", "HIGH", dummyRes.signalQuality);
    }

    // 7. Missing Data Handling -> NO_TRADE & INVALID
    @Test
    public void test7_MissingDataYieldsNoTradeAndInvalidQuality() {
        List<MarketIntelligenceEngine.Bar> insufficientBars = createBullishBars(10, 2600.0);
        TradingDecisionResult result = engine.evaluate(insufficientBars);

        assertEquals("Decision should be NO_TRADE for missing/insufficient data", TradingDecisionResult.Decision.NO_TRADE, result.decision);
        assertEquals("Direction should be UNKNOWN", TradingDecisionResult.Direction.UNKNOWN, result.direction);
        assertEquals("Signal Quality should be INVALID", "INVALID", result.signalQuality);
        assertEquals("Confidence should be 0.0%", 0.0, result.confidence, 0.001);
        assertEquals("Bullish score should be 0", 0, result.bullishScore);
        assertEquals("Bearish score should be 0", 0, result.bearishScore);
    }

    // 8. Invalid Data / Null Handling -> NO_TRADE & INVALID
    @Test
    public void test8_InvalidDataYieldsNoTradeAndInvalidQuality() {
        TradingDecisionResult nullResult = engine.evaluate(null);
        assertEquals("Null bars input should yield NO_TRADE", TradingDecisionResult.Decision.NO_TRADE, nullResult.decision);
        assertEquals("Signal Quality should be INVALID", "INVALID", nullResult.signalQuality);

        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(50, 2600.0);
        TradingDecisionResult outOfBoundsRes = engine.evaluateAtCandle(bars, 100);
        assertEquals("Out of bounds index should yield NO_TRADE", TradingDecisionResult.Decision.NO_TRADE, outOfBoundsRes.decision);
        assertEquals("Signal Quality should be INVALID", "INVALID", outOfBoundsRes.signalQuality);
    }

    // 9. Calculation Consistency and Determinism
    @Test
    public void test9_CalculationConsistencyAndStability() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);

        TradingDecisionResult res1 = engine.evaluate(bars);
        TradingDecisionResult res2 = engine.evaluate(bars);

        assertEquals("Bullish scores must be identical", res1.bullishScore, res2.bullishScore);
        assertEquals("Bearish scores must be identical", res1.bearishScore, res2.bearishScore);
        assertEquals("Total scores must be identical", res1.totalScore, res2.totalScore);
        assertEquals("Confidence values must be identical", res1.confidence, res2.confidence, 0.00001);
        assertEquals("Signal qualities must be identical", res1.signalQuality, res2.signalQuality);
        assertEquals("Decisions must be identical", res1.decision, res2.decision);
    }

    // 10. No Look-Ahead Bias Verification
    @Test
    public void test10_NoLookAheadBias() {
        List<MarketIntelligenceEngine.Bar> fullSeries = createBullishBars(70, 2600.0);

        // Evaluate slice at candle 35
        TradingDecisionResult resAt35 = engine.evaluateAtCandle(fullSeries, 35);

        // Mutate future bars (from index 36 to 69) with extreme price drop
        List<MarketIntelligenceEngine.Bar> mutatedSeries = new ArrayList<>();
        for (int i = 0; i <= 35; i++) {
            mutatedSeries.add(fullSeries.get(i));
        }
        double crashPrice = fullSeries.get(35).close;
        for (int i = 36; i < 70; i++) {
            crashPrice -= 10.0;
            mutatedSeries.add(new MarketIntelligenceEngine.Bar(crashPrice + 5, crashPrice + 10, crashPrice - 5, crashPrice, 5000));
        }

        TradingDecisionResult resAt35Mutated = engine.evaluateAtCandle(mutatedSeries, 35);

        assertEquals("Candle 35 current price must remain identical", resAt35.currentPrice, resAt35Mutated.currentPrice, 0.0001);
        assertEquals("Candle 35 Bullish Score must remain identical", resAt35.bullishScore, resAt35Mutated.bullishScore);
        assertEquals("Candle 35 Bearish Score must remain identical", resAt35.bearishScore, resAt35Mutated.bearishScore);
        assertEquals("Candle 35 Confidence must remain identical", resAt35.confidence, resAt35Mutated.confidence, 0.0001);
        assertEquals("Candle 35 Signal Quality must remain identical", resAt35.signalQuality, resAt35Mutated.signalQuality);
        assertEquals("Candle 35 Decision must remain identical", resAt35.decision, resAt35Mutated.decision);
    }
}
