package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TradeSetupEngineTest {

    private TradeSetupEngine setupEngine;

    @Before
    public void setUp() {
        setupEngine = new TradeSetupEngine();
    }

    private List<MarketIntelligenceEngine.Bar> createTrendBars(boolean bullish, int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double base = 2600.0;
        for (int i = 0; i < count; i++) {
            double step = bullish ? (i * 0.8) : (-i * 0.8);
            double close = base + step;
            double open = close - (bullish ? 0.3 : -0.3);
            double high = Math.max(open, close) + 1.0;
            double low = Math.min(open, close) - 1.0;
            bars.add(new MarketIntelligenceEngine.Bar(open, high, low, close, 1000));
        }
        return bars;
    }

    // 1. Valid BUY Setup
    @Test
    public void testValidBuySetup() {
        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 100);
        TradingDecisionResult result = new TradingDecisionEngine().evaluate(bars);
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 85.0;
        result.currentPrice = 2680.0;
        result.atrValue = 3.0;
        result.support = 2670.0;
        result.resistance = 2710.0;

        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertTrue("BUY setup evaluated by engine should be valid", setup.valid);
        assertEquals(TradeSetup.Direction.BUY, setup.direction);
        assertEquals(2680.0, setup.entryPrice, 0.001);
        assertTrue(setup.stopLoss < setup.entryPrice);
        assertTrue(setup.takeProfit > setup.entryPrice);
        assertTrue(setup.riskRewardRatio >= 1.5);
    }

    // 2. Valid SELL Setup
    @Test
    public void testValidSellSetup() {
        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(false, 100);
        TradingDecisionResult result = new TradingDecisionEngine().evaluate(bars);
        result.decision = TradingDecisionResult.Decision.SELL;
        result.signalQuality = "HIGH";
        result.confidence = 85.0;
        result.currentPrice = 2520.0;
        result.atrValue = 3.0;
        result.support = 2490.0;
        result.resistance = 2530.0;

        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertTrue("SELL setup evaluated by engine should be valid", setup.valid);
        assertEquals(TradeSetup.Direction.SELL, setup.direction);
        assertEquals(2520.0, setup.entryPrice, 0.001);
        assertTrue(setup.stopLoss > setup.entryPrice);
        assertTrue(setup.takeProfit < setup.entryPrice);
        assertTrue(setup.riskRewardRatio >= 1.5);
    }

    // 3. BUY with invalid Stop Loss (Engine rejects when SL >= Entry due to support above entry)
    @Test
    public void testBuyWithInvalidStopLoss() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.support = 2700.0; // Support higher than entry!

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("Engine itself must reject BUY setup when SL >= Entry", setup.valid);
        assertTrue("Explanation must contain Arabic boundary reason", setup.explanation.contains("وقف الخسارة يجب أن يكون أقل"));
    }

    // 4. SELL with invalid Stop Loss (Engine rejects when SL <= Entry due to resistance below entry)
    @Test
    public void testSellWithInvalidStopLoss() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.SELL;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.resistance = 2600.0; // Resistance lower than entry!

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(false, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("Engine itself must reject SELL setup when SL <= Entry", setup.valid);
        assertTrue("Explanation must contain Arabic boundary reason", setup.explanation.contains("وقف الخسارة يجب أن يكون أعلى"));
    }

    // 5. BUY with invalid Take Profit (Engine rejects when TP <= Entry due to resistance below entry)
    @Test
    public void testBuyWithInvalidTakeProfit() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.resistance = 2640.0; // Resistance lower than entry!

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("Engine itself must reject BUY setup when TP <= Entry", setup.valid);
        assertTrue("Explanation must contain Arabic boundary reason", setup.explanation.contains("هدف الربح يجب أن يكون أعلى"));
    }

    // 6. SELL with invalid Take Profit (Engine rejects when TP >= Entry due to support above entry)
    @Test
    public void testSellWithInvalidTakeProfit() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.SELL;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.support = 2660.0; // Support higher than entry!

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(false, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("Engine itself must reject SELL setup when TP >= Entry", setup.valid);
        assertTrue("Explanation must contain Arabic boundary reason", setup.explanation.contains("هدف الربح يجب أن يكون أقل"));
    }

    // 7. Valid Risk/Reward Ratio
    @Test
    public void testValidRiskRewardRatio() {
        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 100);
        TradeSetup setup = setupEngine.createTradeSetup(bars);
        if (setup.valid) {
            assertTrue("Engine-evaluated R:R ratio must be >= 1.5", setup.riskRewardRatio >= 1.5);
        }
    }

    // 8. Risk/Reward below minimum threshold
    @Test
    public void testRiskRewardBelowMinimum() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "MEDIUM";
        result.confidence = 70.0;
        result.currentPrice = 2650.0;
        result.atrValue = 10.0; // large SL distance
        result.support = 2600.0;
        result.resistance = 2655.0; // small TP distance

        TradeSetupEngine customEngine = new TradeSetupEngine(3.0); // Required RR is 3.0
        TradeSetup setup = customEngine.createTradeSetup(result, createTrendBars(true, 50));

        assertFalse("Engine must reject setup with RR below minimum threshold", setup.valid);
    }

    // 9. Improved Risk = 0 test
    @Test
    public void testRiskEqualsZeroHandling() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 0.00000001; // Leads to riskDistance = 0.0

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("Risk = 0 must render setup invalid without crashing", setup.valid);
        assertTrue("Risk distance should be non-positive or negligible", setup.riskDistance <= 0.0001);
    }

    // 10. HIGH Signal Quality
    @Test
    public void testHighSignalQuality() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 90.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals("HIGH", setup.signalQuality);
        assertTrue(setup.valid);
    }

    // 11. LOW Signal Quality
    @Test
    public void testLowSignalQuality() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "LOW";
        result.confidence = 45.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("LOW signal quality setup must be invalid", setup.valid);
    }

    // 12. INVALID Signal Quality
    @Test
    public void testInvalidSignalQuality() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "INVALID";
        result.confidence = 0.0;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertFalse("INVALID signal quality setup must be invalid", setup.valid);
    }

    // 13. TREND_UP Market Regime
    @Test
    public void testTrendUpMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 85.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.TREND_UP;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.TREND_UP, setup.marketRegime);
        assertTrue(setup.valid);
    }

    // 14. TREND_DOWN Market Regime
    @Test
    public void testTrendDownMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.SELL;
        result.signalQuality = "HIGH";
        result.confidence = 85.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.TREND_DOWN;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(false, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.TREND_DOWN, setup.marketRegime);
        assertTrue(setup.valid);
    }

    // 15. RANGE Market Regime
    @Test
    public void testRangeMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "MEDIUM";
        result.confidence = 70.0;
        result.currentPrice = 2650.0;
        result.atrValue = 2.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.RANGE;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.RANGE, setup.marketRegime);
    }

    // 16. HIGH_VOLATILITY Market Regime
    @Test
    public void testHighVolatilityMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 6.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.HIGH_VOLATILITY;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.HIGH_VOLATILITY, setup.marketRegime);
        assertTrue(setup.riskDistance >= 6.0 * 2.0);
    }

    // 17. LOW_VOLATILITY Market Regime
    @Test
    public void testLowVolatilityMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "HIGH";
        result.confidence = 80.0;
        result.currentPrice = 2650.0;
        result.atrValue = 1.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.LOW_VOLATILITY;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.LOW_VOLATILITY, setup.marketRegime);
    }

    // 18. TRANSITION / UNCERTAIN Market Regime
    @Test
    public void testTransitionUncertainMarketRegime() {
        TradingDecisionResult result = new TradingDecisionResult();
        result.decision = TradingDecisionResult.Decision.BUY;
        result.signalQuality = "MEDIUM";
        result.confidence = 65.0;
        result.currentPrice = 2650.0;
        result.atrValue = 3.0;
        result.marketRegime = new MarketRegimeResult();
        result.marketRegime.regime = MarketRegimeResult.Regime.TRANSITION;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setup = setupEngine.createTradeSetup(result, bars);

        assertEquals(MarketRegimeResult.Regime.TRANSITION, setup.marketRegime);
        assertTrue(setup.confidence <= 50.0);
    }

    // 19. Missing Data Handling (Null / Empty)
    @Test
    public void testMissingDataHandling() {
        TradeSetup setup1 = setupEngine.createTradeSetup(null);
        assertFalse("Null bars list should return invalid setup", setup1.valid);

        TradeSetup setup2 = setupEngine.createTradeSetup(new ArrayList<>());
        assertFalse("Empty bars list should return invalid setup", setup2.valid);

        TradeSetup setup3 = setupEngine.createTradeSetup(null, new ArrayList<>());
        assertFalse("Null decisionResult should return invalid setup", setup3.valid);
    }

    // 20. Invalid Data Handling (NaN / Infinity)
    @Test
    public void testInvalidDataHandlingNaNAndInfinity() {
        TradingDecisionResult resultNaN = new TradingDecisionResult();
        resultNaN.decision = TradingDecisionResult.Decision.BUY;
        resultNaN.signalQuality = "HIGH";
        resultNaN.confidence = 80.0;
        resultNaN.currentPrice = Double.NaN;
        resultNaN.atrValue = 3.0;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setupNaN = setupEngine.createTradeSetup(resultNaN, bars);

        assertFalse("NaN entry price must render setup invalid without crashing", setupNaN.valid);

        TradingDecisionResult resultInf = new TradingDecisionResult();
        resultInf.decision = TradingDecisionResult.Decision.BUY;
        resultInf.signalQuality = "HIGH";
        resultInf.confidence = 80.0;
        resultInf.currentPrice = 2650.0;
        resultInf.atrValue = Double.POSITIVE_INFINITY;

        TradeSetup setupInf = setupEngine.createTradeSetup(resultInf, bars);

        assertFalse("Infinity ATR must render setup invalid without crashing", setupInf.valid);
    }

    // 21. BUY / SELL Direction Consistency
    @Test
    public void testDirectionConsistency() {
        TradingDecisionResult resultWait = new TradingDecisionResult();
        resultWait.decision = TradingDecisionResult.Decision.WAIT;

        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 50);
        TradeSetup setupWait = setupEngine.createTradeSetup(resultWait, bars);

        assertEquals(TradeSetup.Direction.NONE, setupWait.direction);
        assertFalse(setupWait.valid);
    }

    // 22. Look-Ahead Bias Protection
    @Test
    public void testLookAheadBiasProtection() {
        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 100);

        TradeSetup setup1 = setupEngine.evaluateAtCandle(bars, 60);

        List<MarketIntelligenceEngine.Bar> extendedBars = new ArrayList<>(bars);
        for (int i = 0; i < 40; i++) {
            extendedBars.add(new MarketIntelligenceEngine.Bar(3000 + i, 3100 + i, 2900 + i, 3050 + i, 5000));
        }

        TradeSetup setup2 = setupEngine.evaluateAtCandle(extendedBars, 60);

        assertEquals("Entry price at candle 60 must be identical despite future data", setup1.entryPrice, setup2.entryPrice, 0.0001);
        assertEquals("Stop loss at candle 60 must be identical despite future data", setup1.stopLoss, setup2.stopLoss, 0.0001);
        assertEquals("Take profit at candle 60 must be identical despite future data", setup1.takeProfit, setup2.takeProfit, 0.0001);
        assertEquals("Validity state at candle 60 must be identical despite future data", setup1.valid, setup2.valid);
    }

    // 23. Consistency / Deterministic Result
    @Test
    public void testDeterministicResult() {
        List<MarketIntelligenceEngine.Bar> bars = createTrendBars(true, 80);

        TradeSetup setupA = setupEngine.createTradeSetup(bars);
        TradeSetup setupB = setupEngine.createTradeSetup(bars);

        assertEquals(setupA.valid, setupB.valid);
        assertEquals(setupA.direction, setupB.direction);
        assertEquals(setupA.entryPrice, setupB.entryPrice, 0.0001);
        assertEquals(setupA.stopLoss, setupB.stopLoss, 0.0001);
        assertEquals(setupA.takeProfit, setupB.takeProfit, 0.0001);
        assertEquals(setupA.riskRewardRatio, setupB.riskRewardRatio, 0.0001);
    }

    // 24. Invalid minRiskRewardRatio Sanitization Handling
    @Test
    public void testInvalidMinRiskRewardRatioSanitization() {
        TradeSetupEngine engineZero = new TradeSetupEngine(0.0);
        assertEquals("Zero minRiskRewardRatio must fall back to 1.5", 1.5, engineZero.getMinRiskRewardRatio(), 0.001);

        TradeSetupEngine engineNeg = new TradeSetupEngine(-2.0);
        assertEquals("Negative minRiskRewardRatio must fall back to 1.5", 1.5, engineNeg.getMinRiskRewardRatio(), 0.001);

        TradeSetupEngine engineNaN = new TradeSetupEngine(Double.NaN);
        assertEquals("NaN minRiskRewardRatio must fall back to 1.5", 1.5, engineNaN.getMinRiskRewardRatio(), 0.001);

        TradeSetupEngine engineInf = new TradeSetupEngine(Double.POSITIVE_INFINITY);
        assertEquals("Infinity minRiskRewardRatio must fall back to 1.5", 1.5, engineInf.getMinRiskRewardRatio(), 0.001);
    }
}
