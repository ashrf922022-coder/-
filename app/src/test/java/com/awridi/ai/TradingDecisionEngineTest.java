package com.awridi.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TradingDecisionEngineTest {

    private List<MarketIntelligenceEngine.Bar> createBullishBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2600.0;
        for (int i = 0; i < count; i++) {
            // Pattern: 2 down bars (-0.5), 3 up bars (+0.8)
            // Indices ending in 2, 3, 4 (including bar 57, 58, 59) are UP bars!
            double change = (i % 5 == 0 || i % 5 == 1) ? -0.5 : 0.8;
            price += change;
            double open = price - change;
            double close = price;
            double high = Math.max(open, close) + 0.3;
            double low = Math.min(open, close) - 0.3;
            double volume = 2000 + i * 20;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createBearishBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2700.0;
        for (int i = 0; i < count; i++) {
            // Pattern: 2 up bars (+0.5), 3 down bars (-0.8)
            // Indices ending in 2, 3, 4 (including bar 57, 58, 59) are DOWN bars!
            double change = (i % 5 == 0 || i % 5 == 1) ? 0.5 : -0.8;
            price += change;
            double open = price - change;
            double close = price;
            double high = Math.max(open, close) + 0.3;
            double low = Math.min(open, close) - 0.3;
            double volume = 2000 + i * 20;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketIntelligenceEngine.Bar> createConflictingBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2650.0;
        for (int i = 0; i < count; i++) {
            // Alternating sharp up and down swings (chop/conflict)
            double change = (i % 2 == 0) ? 8.0 : -8.0;
            price += change;
            double open = price - change;
            double close = price;
            double high = Math.max(open, close) + 5.0;
            double low = Math.min(open, close) - 5.0;
            double volume = 1500;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, volume));
        }
        return bars;
    }

    // 1. Clear BUY setup case
    @Test
    public void testClearBuySetup() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertNotNull("Result should not be null", result);
        assertEquals("Decision should be BUY for clear bullish sequence", TradingDecisionEngine.Decision.BUY, result.decisionEnum);
        assertEquals("BUY", result.decision);
        assertTrue("Confidence score should be >= 60%", result.confidenceScore >= 0.60);
        assertFalse("Supporting factors should not be empty", result.supportingFactors.isEmpty());
    }

    // 2. Clear SELL setup case
    @Test
    public void testClearSellSetup() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertNotNull("Result should not be null", result);
        assertEquals("Decision should be SELL for clear bearish sequence", TradingDecisionEngine.Decision.SELL, result.decisionEnum);
        assertEquals("SELL", result.decision);
        assertTrue("Confidence score should be >= 60%", result.confidenceScore >= 0.60);
        assertFalse("Conflicting/bearish factors should not be empty", result.conflictingFactors.isEmpty());
    }

    // 3. Clear WAIT setup case (conflicting indicators)
    @Test
    public void testClearWaitSetupConflictingIndicators() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createConflictingBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertNotNull("Result should not be null", result);
        assertEquals("Decision should be WAIT when indicators conflict or chop", TradingDecisionEngine.Decision.WAIT, result.decisionEnum);
        assertEquals("WAIT", result.decision);
    }

    // 4. Clear NO TRADE setup case due to insufficient data (< 30 bars)
    @Test
    public void testNoTradeCaseInsufficientData() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> shortBars = createBullishBars(15); // < 30 bars

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(shortBars, "XAU/USD");

        assertNotNull("Result should not be null", result);
        assertEquals("Decision should be NO TRADE for insufficient data", TradingDecisionEngine.Decision.NO_TRADE, result.decisionEnum);
        assertEquals("NO TRADE", result.decision);
        assertEquals("Confidence score should be 0 for insufficient data", 0.0, result.confidenceScore, 0.001);
        assertTrue("Explanation should mention insufficient data", result.explanation.contains("غير كافية"));
    }

    // 5. Bullish trend detection
    @Test
    public void testBullishTrendDetection() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertTrue("Trend should contain bullish text", result.trend.contains("صاعد"));
        assertTrue("EMA20 should be greater than EMA50", result.ema20 > result.ema50);
    }

    // 6. Bearish trend detection
    @Test
    public void testBearishTrendDetection() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertTrue("Trend should contain bearish text", result.trend.contains("هابط"));
        assertTrue("EMA20 should be less than EMA50", result.ema20 < result.ema50);
    }

    // 7. Bullish momentum
    @Test
    public void testBullishMomentum() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertTrue("MACD Histogram should be positive for bullish momentum", result.macdHistogram > 0);
        assertTrue("RSI should be >= 45 for bullish momentum", result.rsi >= 45.0);
    }

    // 8. Bearish momentum
    @Test
    public void testBearishMomentum() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertTrue("MACD Histogram should be negative for bearish momentum", result.macdHistogram < 0);
        assertTrue("RSI should be <= 55 for bearish momentum", result.rsi <= 55.0);
    }

    // 9. High volatility handling
    @Test
    public void testHighVolatilityHandling() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createConflictingBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertTrue("ATR should be calculated and greater than 0", result.atr > 0);
        assertNotNull("Volatility text should be populated", result.volatility);
    }

    // 10. Historical data evaluation without look-ahead bias
    @Test
    public void testNoLookAheadBias() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> fullBars = createBullishBars(80);

        // Evaluate sublist up to bar 50
        List<MarketIntelligenceEngine.Bar> subList = fullBars.subList(0, 50);

        TradingDecisionEngine.TradingDecisionResult subResult = engine.evaluateDecision(subList, "XAU/USD");

        // Bar 50 close price in subList evaluation must equal subResult.currentPrice
        assertEquals("Sublist evaluation current price must equal 50th bar close price", fullBars.get(49).close, subResult.currentPrice, 0.001);
        assertNotEquals("Sublist current price should differ from 80th bar close price", fullBars.get(79).close, subResult.currentPrice, 0.001);
    }

    // 11. Result explainability check
    @Test
    public void testResultExplainability() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60);

        TradingDecisionEngine.TradingDecisionResult result = engine.evaluateDecision(bars, "XAU/USD");

        assertNotNull("Explanation should not be null", result.explanation);
        assertFalse("Explanation should not be empty", result.explanation.trim().isEmpty());
        assertTrue("Explanation should contain current price reading", result.explanation.contains("السعر الحالي"));
        assertTrue("Explanation should contain decision justification", result.explanation.contains("مبررات"));
    }

    // 12. Null-safety & crash prevention check
    @Test
    public void testNullSafetyAndCrashPrevention() {
        TradingDecisionEngine engine = new TradingDecisionEngine();

        TradingDecisionEngine.TradingDecisionResult nullBarsRes = engine.evaluateDecision(null, "XAU/USD");
        assertNotNull("Result should not be null for null bars list", nullBarsRes);
        assertEquals("Decision should be NO TRADE for null bars list", TradingDecisionEngine.Decision.NO_TRADE, nullBarsRes.decisionEnum);

        TradingDecisionEngine.TradingDecisionResult emptyBarsRes = engine.evaluateDecision(new ArrayList<>(), null);
        assertNotNull("Result should not be null for empty bars list", emptyBarsRes);
        assertEquals("Decision should be NO TRADE for empty bars list", TradingDecisionEngine.Decision.NO_TRADE, emptyBarsRes.decisionEnum);
        assertEquals("XAU/USD", emptyBarsRes.symbol);
    }
}
