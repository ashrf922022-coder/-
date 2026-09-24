package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RiskManagementEngineTest {

    private RiskManagementEngine engine;

    @Before
    public void setUp() {
        engine = new RiskManagementEngine();
    }

    // 1. Correct Risk Amount calculation
    @Test
    public void testCorrectRiskAmountCalculation() {
        // Balance = 10,000, Risk% = 1.0 -> Risk Amount = 100
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertTrue(res.valid);
        assertEquals(100.0, res.riskAmount, 0.001);
    }

    // 2. Correct Position Size calculation
    @Test
    public void testCorrectPositionSizeCalculation() {
        // Balance = 10,000, Risk% = 1%, Risk Amount = $100
        // Entry = 2600, SL = 2590 -> Risk Distance = $10
        // XAU/USD 1 Lot = $100 move per $1 price move -> 10 * 100 = $1000 per lot
        // Lot size = 100 / 1000 = 0.1 Lot
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertTrue(res.valid);
        assertEquals(0.1, res.positionSize, 0.001);
    }

    // 3. BUY scenario
    @Test
    public void testBuyScenario() {
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertTrue(res.valid);
        assertEquals(10.0, res.riskDistance, 0.001);
        assertEquals(20.0, res.rewardDistance, 0.001);
        assertEquals(2.0, res.riskRewardRatio, 0.001);
    }

    // 4. SELL scenario
    @Test
    public void testSellScenario() {
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2610.0, 2580.0, TradeSetup.Direction.SELL, 0.0);
        assertTrue(res.valid);
        assertEquals(10.0, res.riskDistance, 0.001);
        assertEquals(20.0, res.rewardDistance, 0.001);
        assertEquals(2.0, res.riskRewardRatio, 0.001);
        assertEquals(0.1, res.positionSize, 0.001);
    }

    // 5. Invalid Stop Loss (BUY SL >= Entry or SELL SL <= Entry)
    @Test
    public void testInvalidStopLoss() {
        // BUY SL above Entry
        RiskManagementEngine.RiskResult resBuy = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2610.0, 2630.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resBuy.valid);

        // SELL SL below Entry
        RiskManagementEngine.RiskResult resSell = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2570.0, TradeSetup.Direction.SELL, 0.0);
        assertFalse(resSell.valid);
    }

    // 6. Invalid Entry Price
    @Test
    public void testInvalidEntryPrice() {
        RiskManagementEngine.RiskResult resZero = engine.evaluateRisk(10000.0, 1.0, 0.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resZero.valid);

        RiskManagementEngine.RiskResult resNeg = engine.evaluateRisk(10000.0, 1.0, -100.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resNeg.valid);
    }

    // 7. Account Balance = 0 or negative
    @Test
    public void testAccountBalanceZeroOrNegative() {
        RiskManagementEngine.RiskResult resZero = engine.evaluateRisk(0.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resZero.valid);

        RiskManagementEngine.RiskResult resNeg = engine.evaluateRisk(-500.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resNeg.valid);
    }

    // 8. Risk Percentage = 0 or negative
    @Test
    public void testRiskPercentageZeroOrNegative() {
        RiskManagementEngine.RiskResult resZero = engine.evaluateRisk(10000.0, 0.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resZero.valid);

        RiskManagementEngine.RiskResult resNeg = engine.evaluateRisk(10000.0, -1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(resNeg.valid);
    }

    // 9. Risk Percentage greater than maximum allowed (default max 2%)
    @Test
    public void testRiskPercentageGreaterThanMax() {
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 2.5, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res.valid);
        assertTrue(res.rejectionReason.contains("تتجاوز الحد الأقصى"));
    }

    // 10. NaN input handling
    @Test
    public void testNaNInputHandling() {
        RiskManagementEngine.RiskResult res1 = engine.evaluateRisk(Double.NaN, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res1.valid);

        RiskManagementEngine.RiskResult res2 = engine.evaluateRisk(10000.0, Double.NaN, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res2.valid);

        RiskManagementEngine.RiskResult res3 = engine.evaluateRisk(10000.0, 1.0, Double.NaN, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res3.valid);
    }

    // 11. Infinity input handling
    @Test
    public void testInfinityInputHandling() {
        RiskManagementEngine.RiskResult res1 = engine.evaluateRisk(Double.POSITIVE_INFINITY, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res1.valid);

        RiskManagementEngine.RiskResult res2 = engine.evaluateRisk(10000.0, Double.NEGATIVE_INFINITY, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res2.valid);
    }

    // 12. Risk/Reward less than 1.5
    @Test
    public void testRiskRewardLessThanMin() {
        // Entry = 2600, SL = 2590 (Risk = 10), TP = 2610 (Reward = 10) -> R:R = 1.0 < 1.5
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2610.0, TradeSetup.Direction.BUY, 0.0);
        assertFalse(res.valid);
        assertTrue(res.rejectionReason.contains("أقل من الحد الأدنى المقبول"));
    }

    // 13. Risk/Reward valid (>= 1.5)
    @Test
    public void testRiskRewardValid() {
        // Entry = 2600, SL = 2590 (Risk = 10), TP = 2615 (Reward = 15) -> R:R = 1.5
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2615.0, TradeSetup.Direction.BUY, 0.0);
        assertTrue(res.valid);
        assertEquals(1.5, res.riskRewardRatio, 0.001);
    }

    // 14. Maximum Position Size capping
    @Test
    public void testMaximumPositionSizeCapping() {
        // Tight SL (e.g. 0.1 USD distance) on 100 USD risk -> Lot size = 100 / (0.1 * 100) = 10.0
        // Set custom max position size = 2.0 Lots
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2599.9, 2600.2, TradeSetup.Direction.BUY, 0.0, 2.0);
        assertTrue(res.valid);
        assertEquals(2.0, res.positionSize, 0.001);
        assertFalse(res.warnings.isEmpty());
    }

    // 15. Daily Loss below limit
    @Test
    public void testDailyLossBelowLimit() {
        // Balance = 10,000, Max Daily Loss % = 3% -> $300 limit
        // Current Daily Loss = $100 -> valid
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 100.0);
        assertTrue(res.valid);
    }

    // 16. Daily Loss equals limit
    @Test
    public void testDailyLossEqualsLimit() {
        // Balance = 10,000, Max Daily Loss = $300. Current Daily Loss = $300 -> blocked
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 300.0);
        assertFalse(res.valid);
        assertEquals("تم الوصول إلى الحد الأقصى للخسارة اليومية", res.rejectionReason);
    }

    // 17. Daily Loss exceeds limit
    @Test
    public void testDailyLossExceedsLimit() {
        // Balance = 10,000, Max Daily Loss = $300. Current Daily Loss = $350 -> blocked
        RiskManagementEngine.RiskResult res = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 350.0);
        assertFalse(res.valid);
        assertEquals("تم الوصول إلى الحد الأقصى للخسارة اليومية", res.rejectionReason);
    }

    // 18. Invalid TradeSetup
    @Test
    public void testInvalidTradeSetup() {
        TradeSetup setup = new TradeSetup();
        setup.valid = false;
        setup.entryPrice = 2600.0;

        RiskManagementEngine.RiskResult resNull = engine.evaluateTradeSetupRisk(null, 10000.0, 1.0, 0.0);
        assertFalse(resNull.valid);

        RiskManagementEngine.RiskResult resInvalid = engine.evaluateTradeSetupRisk(setup, 10000.0, 1.0, 0.0);
        assertFalse(resInvalid.valid);
    }

    // 19. Same data gives same Position Size (Deterministic Output)
    @Test
    public void testDeterministicOutput() {
        RiskManagementEngine.RiskResult res1 = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);
        RiskManagementEngine.RiskResult res2 = engine.evaluateRisk(10000.0, 1.0, 2600.0, 2590.0, 2620.0, TradeSetup.Direction.BUY, 0.0);

        assertTrue(res1.valid);
        assertTrue(res2.valid);
        assertEquals(res1.positionSize, res2.positionSize, 0.000001);
        assertEquals(res1.riskAmount, res2.riskAmount, 0.000001);
    }

    // 20. No crash with completely invalid data
    @Test
    public void testNoCrashWithInvalidData() {
        assertNotNull(engine.evaluateRisk(-1.0, -1.0, -1.0, -1.0, -1.0, null, -10.0));
        assertNotNull(engine.evaluateRisk(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, Double.NaN));
        assertNotNull(engine.evaluateRisk(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0, 0.0, TradeSetup.Direction.NONE, 0.0));
    }

    // 21. Look-Ahead Protection Test
    @Test
    public void testLookAheadProtection() {
        // Create initial historical bars up to index T
        List<MarketIntelligenceEngine.Bar> baseBars = new ArrayList<>();
        double price = 2600.0;
        for (int i = 0; i < 50; i++) {
            baseBars.add(new MarketIntelligenceEngine.Bar(price, price + 2, price - 2, price + 1, 1000));
            price += 0.5;
        }

        // Create a setup evaluated at the current bar T
        TradeSetupEngine setupEngine = new TradeSetupEngine();
        TradeSetup setup1 = setupEngine.evaluateAtCandle(baseBars, 49);

        RiskManagementEngine.RiskResult risk1 = engine.evaluateTradeSetupRisk(setup1, 10000.0, 1.0, 0.0);

        // Now append 50 future candles (T+1 to T+50)
        List<MarketIntelligenceEngine.Bar> extendedBars = new ArrayList<>(baseBars);
        for (int i = 0; i < 50; i++) {
            extendedBars.add(new MarketIntelligenceEngine.Bar(price + 5, price + 10, price - 5, price + 8, 2000));
            price += 1.0;
        }

        // Re-evaluate strictly at historical index 49
        TradeSetup setup2 = setupEngine.evaluateAtCandle(extendedBars, 49);
        RiskManagementEngine.RiskResult risk2 = engine.evaluateTradeSetupRisk(setup2, 10000.0, 1.0, 0.0);

        // Result at index 49 must be identical regardless of future candles
        assertEquals(risk1.valid, risk2.valid);
        if (risk1.valid) {
            assertEquals(risk1.positionSize, risk2.positionSize, 0.000001);
            assertEquals(risk1.riskAmount, risk2.riskAmount, 0.000001);
            assertEquals(risk1.entryPrice, risk2.entryPrice, 0.000001);
            assertEquals(risk1.stopLoss, risk2.stopLoss, 0.000001);
        }
    }
}
