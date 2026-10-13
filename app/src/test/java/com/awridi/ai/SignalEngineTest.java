package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SignalEngineTest {

    private SignalEngine signalEngine;

    @Before
    public void setUp() {
        signalEngine = new SignalEngine(1.5, 60.0);
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
    public void testBuySignal() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        SignalEngine.SignalResult result = signalEngine.generateSignal(bars);

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(SignalEngine.SignalType.BUY, result.signalType);
        assertEquals(SignalEngine.Trend.BULLISH, result.trend);
        assertTrue(result.suggestedStopLoss < result.entryPrice);
        assertTrue(result.suggestedTakeProfit > result.entryPrice);
        assertTrue(result.suggestedRiskReward >= 1.0);
        assertTrue(result.confidence >= 60.0);
    }

    @Test
    public void testSellSignal() {
        List<MarketIntelligenceEngine.Bar> bars = createDowntrendBars(50);
        SignalEngine.SignalResult result = signalEngine.generateSignal(bars);

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(SignalEngine.SignalType.SELL, result.signalType);
        assertEquals(SignalEngine.Trend.BEARISH, result.trend);
        assertTrue(result.suggestedStopLoss > result.entryPrice);
        assertTrue(result.suggestedTakeProfit < result.entryPrice);
        assertTrue(result.suggestedRiskReward >= 1.0);
        assertTrue(result.confidence >= 60.0);
    }

    @Test
    public void testHoldSignalInSidewaysMarket() {
        List<MarketIntelligenceEngine.Bar> bars = createSidewaysBars(50);
        SignalEngine.SignalResult result = signalEngine.generateSignal(bars);

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(SignalEngine.SignalType.HOLD, result.signalType);
        assertEquals(SignalEngine.Trend.SIDEWAYS, result.trend);
    }

    @Test
    public void testInsufficientData() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(15); // < 30 bars
        SignalEngine.SignalResult result = signalEngine.generateSignal(bars);

        assertNotNull(result);
        assertFalse(result.valid);
        assertEquals(SignalEngine.SignalType.HOLD, result.signalType);
        assertTrue(result.rejectionReason.contains("غير كافٍ"));
    }

    @Test
    public void testInvalidDataNullOrEmpty() {
        SignalEngine.SignalResult resultNull = signalEngine.generateSignal(null);
        assertNotNull(resultNull);
        assertFalse(resultNull.valid);
        assertEquals(SignalEngine.SignalType.HOLD, resultNull.signalType);

        SignalEngine.SignalResult resultEmpty = signalEngine.generateSignal(new ArrayList<>());
        assertNotNull(resultEmpty);
        assertFalse(resultEmpty.valid);
        assertEquals(SignalEngine.SignalType.HOLD, resultEmpty.signalType);
    }

    @Test
    public void testInvalidBarPrices() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(35);
        bars.get(bars.size() - 1).close = Double.NaN;

        SignalEngine.SignalResult result = signalEngine.generateSignal(bars);
        assertNotNull(result);
        assertFalse(result.valid);
        assertEquals(SignalEngine.SignalType.HOLD, result.signalType);
        assertTrue(result.rejectionReason.contains("غير صالح"));
    }

    @Test
    public void testHighConfidenceThresholdConvertsToHold() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);

        // Set mandatory confidence threshold to 101.0% (impossible threshold)
        SignalEngine strictEngine = new SignalEngine(1.5, 101.0);
        SignalEngine.SignalResult result = strictEngine.generateSignal(bars);

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(SignalEngine.SignalType.HOLD, result.signalType); // Converted to HOLD due to confidence < 101.0%
    }

    @Test
    public void testUnacceptableRiskRewardConvertsToHold() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);

        // Set minimum required Risk/Reward to an impossible 10.0
        SignalEngine strictRrEngine = new SignalEngine(10.0, 50.0);
        SignalEngine.SignalResult result = strictRrEngine.generateSignal(bars);

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(SignalEngine.SignalType.HOLD, result.signalType);
    }

    @Test
    public void testPreventionOfLookAheadBias() {
        List<MarketIntelligenceEngine.Bar> originalBars = createUptrendBars(40);
        int targetIndex = 35;

        // Generate signal at index 35 on original list of 40 bars
        SignalEngine.SignalResult sig1 = signalEngine.generateSignalAtCandle(originalBars, targetIndex);

        // Now append 50 extra future bars to the list
        List<MarketIntelligenceEngine.Bar> extendedBars = new ArrayList<>(originalBars);
        extendedBars.addAll(createDowntrendBars(50));

        // Generate signal at the SAME index 35 on the extended list
        SignalEngine.SignalResult sig2 = signalEngine.generateSignalAtCandle(extendedBars, targetIndex);

        // Future bars MUST NOT alter the signal, confidence, or SL/TP calculated at index 35
        assertEquals(sig1.signalType, sig2.signalType);
        assertEquals(sig1.entryPrice, sig2.entryPrice, 0.0001);
        assertEquals(sig1.suggestedStopLoss, sig2.suggestedStopLoss, 0.0001);
        assertEquals(sig1.suggestedTakeProfit, sig2.suggestedTakeProfit, 0.0001);
        assertEquals(sig1.confidence, sig2.confidence, 0.0001);
    }

    @Test
    public void testIntegrationWithTradeSetupAndRiskManagementAcceptance() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        SignalEngine.SignalResult signalResult = signalEngine.generateSignal(bars);

        TradeSetupEngine setupEngine = new TradeSetupEngine();
        TradeSetup setup = setupEngine.createTradeSetupFromSignal(signalResult);

        assertTrue(setup.valid);
        assertEquals(TradeSetup.Direction.BUY, setup.direction);
        assertTrue(setup.entryPrice > 0);
        assertTrue(setup.stopLoss < setup.entryPrice);
        assertTrue(setup.takeProfit > setup.entryPrice);

        RiskManagementEngine riskEngine = new RiskManagementEngine();
        RiskManagementEngine.RiskResult riskResult = riskEngine.evaluateTradeSetupRisk(setup, 10000.0, 1.0, 0.0);

        assertTrue(riskResult.valid);
        assertEquals(10000.0, riskResult.accountBalance, 0.01);
        assertEquals(100.0, riskResult.riskAmount, 0.01);
        assertTrue(riskResult.positionSize > 0);
    }

    @Test
    public void testIntegrationWithTradeSetupAndRiskManagementRejection() {
        List<MarketIntelligenceEngine.Bar> bars = createUptrendBars(50);
        SignalEngine.SignalResult signalResult = signalEngine.generateSignal(bars);

        TradeSetupEngine setupEngine = new TradeSetupEngine();
        TradeSetup setup = setupEngine.createTradeSetupFromSignal(signalResult);

        RiskManagementEngine riskEngine = new RiskManagementEngine();
        riskEngine.setMaxDailyLossPercentage(3.0);

        // Account balance = 10,000, Max daily loss = $300. Current daily loss = $350 (Exceeded daily limit!)
        RiskManagementEngine.RiskResult riskResult = riskEngine.evaluateTradeSetupRisk(setup, 10000.0, 1.0, 350.0);

        assertFalse(riskResult.valid);
        assertTrue(riskResult.rejectionReason.contains("الحد الأقصى للخسارة اليومية"));
    }
}
