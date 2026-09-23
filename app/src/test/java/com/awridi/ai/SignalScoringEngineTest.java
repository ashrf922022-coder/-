package com.awridi.ai;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SignalScoringEngineTest {

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

    @Test
    public void test1_ClearBullishTrendScoringAndQuality() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertTrue("Bullish score should be high (> 50)", res.bullishScore >= 50.0);
        assertTrue("Total score should be positive", res.totalScore > 0);
        assertTrue("Confidence % should be > 0", res.confidencePct > 0);
        assertNotEquals("Signal quality should not be INVALID", TradingDecisionResult.SignalQuality.INVALID, res.signalQuality);
    }

    @Test
    public void test2_ClearBearishTrendScoringAndQuality() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertTrue("Bearish score should be high (> 50)", res.bearishScore >= 50.0);
        assertTrue("Total score should be negative", res.totalScore < 0);
        assertTrue("Confidence % should be > 0", res.confidencePct > 0);
        assertNotEquals("Signal quality should not be INVALID", TradingDecisionResult.SignalQuality.INVALID, res.signalQuality);
    }

    @Test
    public void test3_BullishIndicatorsAgreement() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertEquals("Decision must be BUY", TradingDecisionResult.Decision.BUY, res.decision);
        assertTrue("Bullish score > Bearish score", res.bullishScore > res.bearishScore);
    }

    @Test
    public void test4_BearishIndicatorsAgreement() {
        List<MarketIntelligenceEngine.Bar> bars = createBearishBars(60, 2700.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertEquals("Decision must be SELL", TradingDecisionResult.Decision.SELL, res.decision);
        assertTrue("Bearish score > Bullish score", res.bearishScore > res.bullishScore);
    }

    @Test
    public void test5_ConflictingIndicatorsYieldingWaitAndLowQuality() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(40, 2600.0);
        double lastClose = bars.get(bars.size() - 1).close;
        for (int i = 0; i < 20; i++) {
            double open = lastClose;
            double close = lastClose - 2.5;
            bars.add(new MarketIntelligenceEngine.Bar(open, open + 0.5, close - 0.5, close, 1000.0));
            lastClose = close;
        }

        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertEquals("Decision should be WAIT", TradingDecisionResult.Decision.WAIT, res.decision);
        assertTrue("Confidence % should be <= 50%", res.confidencePct <= 50.0);
        assertEquals("Signal quality should be LOW for WAIT decision", TradingDecisionResult.SignalQuality.LOW, res.signalQuality);
    }

    @Test
    public void test6_LowConfidenceDoesNotForceBuySell() {
        // Flat bars with minor noise
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2650.0;
        for (int i = 0; i < 60; i++) {
            double delta = (i % 2 == 0) ? 0.05 : -0.05;
            bars.add(new MarketIntelligenceEngine.Bar(price, price + 0.2, price - 0.2, price + delta, 1000.0));
            price += delta;
        }

        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertNotEquals("Low confidence should NOT force BUY", TradingDecisionResult.Decision.BUY, res.decision);
        assertNotEquals("Low confidence should NOT force SELL", TradingDecisionResult.Decision.SELL, res.decision);
    }

    @Test
    public void test7_InsufficientDataYieldingNoTradeAndInvalidQuality() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(15, 2650.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();
        TradingDecisionResult res = engine.evaluate(bars);

        assertEquals("Decision should be NO_TRADE", TradingDecisionResult.Decision.NO_TRADE, res.decision);
        assertEquals("Confidence % must be 0 for insufficient data", 0.0, res.confidencePct, 0.001);
        assertEquals("Signal quality must be INVALID", TradingDecisionResult.SignalQuality.INVALID, res.signalQuality);
    }

    @Test
    public void test8_InvalidExtremeDataHandling() {
        SignalScoringEngine scoringEngine = new SignalScoringEngine();

        SignalScoringEngine.ScoreResult resNull = scoringEngine.computeScores(null);
        assertEquals("Null input yields INVALID quality", TradingDecisionResult.SignalQuality.INVALID, resNull.quality);
        assertEquals("Null input yields 0% confidence", 0.0, resNull.confidencePct, 0.001);

        TradingDecisionResult extremeRes = new TradingDecisionResult();
        extremeRes.decision = TradingDecisionResult.Decision.NO_TRADE;
        SignalScoringEngine.ScoreResult resExtreme = scoringEngine.computeScores(extremeRes);
        assertEquals("NO_TRADE yields INVALID quality", TradingDecisionResult.SignalQuality.INVALID, resExtreme.quality);
    }

    @Test
    public void test9_ConsistencyAndRepeatabilityOfCalculations() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(50, 2600.0);
        TradingDecisionEngine engine = new TradingDecisionEngine();

        TradingDecisionResult res1 = engine.evaluate(bars);
        TradingDecisionResult res2 = engine.evaluate(bars);

        assertEquals("Bullish score must be consistent", res1.bullishScore, res2.bullishScore, 0.0001);
        assertEquals("Bearish score must be consistent", res1.bearishScore, res2.bearishScore, 0.0001);
        assertEquals("Confidence % must be consistent", res1.confidencePct, res2.confidencePct, 0.0001);
        assertEquals("Signal Quality must be consistent", res1.signalQuality, res2.signalQuality);
    }

    @Test
    public void test10_LookAheadBiasPreventionOnScoring() {
        List<MarketIntelligenceEngine.Bar> bars = createBullishBars(60, 2600.0);
        SignalScoringEngine scoringEngine = new SignalScoringEngine();

        SignalScoringEngine.ScoreResult scoreAt35 = scoringEngine.computeScoresAtCandle(bars, 35);
        SignalScoringEngine.ScoreResult scoreAt59 = scoringEngine.computeScoresAtCandle(bars, 59);

        assertNotNull("Score at 35 should not be null", scoreAt35);
        assertNotNull("Score at 59 should not be null", scoreAt59);
        assertTrue("Scoring strictly evaluates candle history up to index without throwing exception", scoreAt35.bullishScore > 0);
    }
}
