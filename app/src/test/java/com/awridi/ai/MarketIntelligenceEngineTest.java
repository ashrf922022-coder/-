package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MarketIntelligenceEngineTest {

    private List<MainActivity.Bar> sampleBars;

    @Before
    public void setUp() {
        sampleBars = new ArrayList<>();
        double price = 2600.0;
        for (int i = 0; i < 200; i++) {
            price += (i % 2 == 0 ? 1.5 : -1.0);
            sampleBars.add(new MainActivity.Bar(price - 1, price + 2, price - 2, price, 100 + i));
        }
    }

    @Test
    public void testCalcSMA() {
        double sma20 = MarketIntelligenceEngine.calcSMA(sampleBars, 20, sampleBars.size() - 1);
        assertTrue(sma20 > 2500.0 && sma20 < 2700.0);
    }

    @Test
    public void testCalcEMA() {
        double ema20 = MarketIntelligenceEngine.calcEMA(sampleBars, 20, sampleBars.size() - 1);
        assertTrue(ema20 > 2500.0 && ema20 < 2700.0);
    }

    @Test
    public void testCalcRSI() {
        double rsi = MarketIntelligenceEngine.calcRSI(sampleBars, 14, sampleBars.size() - 1);
        assertTrue("RSI should be between 0 and 100", rsi >= 0.0 && rsi <= 100.0);
    }

    @Test
    public void testCalcATR() {
        double atr = MarketIntelligenceEngine.calcATR(sampleBars, 14, sampleBars.size() - 1);
        assertTrue("ATR should be positive", atr > 0.0);
    }

    @Test
    public void testCalcBollingerBands() {
        double[] bb = MarketIntelligenceEngine.calcBollingerBands(sampleBars, 20, 2.0, sampleBars.size() - 1);
        assertEquals(3, bb.length);
        assertTrue("Upper band should be >= Middle band", bb[0] >= bb[1]);
        assertTrue("Middle band should be >= Lower band", bb[1] >= bb[2]);
    }

    @Test
    public void testMarketIntelligenceAnalysis() {
        Map<String, List<MainActivity.Bar>> mtfBars = new HashMap<>();
        mtfBars.put("15m", sampleBars);
        mtfBars.put("1h", sampleBars);

        MarketIntelligenceResult result = MarketIntelligenceEngine.analyze(mtfBars, "XAU/USD", "15m", 150);

        assertNotNull(result);
        assertEquals("XAU/USD", result.symbol);
        assertTrue("Intelligence Score should be between 0 and 100", result.intelligenceScore >= 0.0 && result.intelligenceScore <= 100.0);
        assertNotNull(result.finalSignal);
        assertNotNull(result.patternType);
        assertFalse(result.scoreFactors.isEmpty());
    }
}
