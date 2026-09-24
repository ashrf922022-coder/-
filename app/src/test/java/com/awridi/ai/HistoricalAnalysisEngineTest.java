package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class HistoricalAnalysisEngineTest {

    private HistoricalAnalysisEngine historicalAnalysisEngine;

    @Before
    public void setUp() {
        historicalAnalysisEngine = new HistoricalAnalysisEngine();
    }

    private List<MarketIntelligenceEngine.Bar> createMixedBars(int count) {
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double price = 2600.0;
        for (int i = 0; i < count; i++) {
            if (i < 40) {
                price += 1.2; // Uptrend phase
            } else if (i < 70) {
                price -= 1.2; // Downtrend phase
            } else {
                price += (i % 2 == 0) ? 0.3 : -0.3; // Sideways phase
            }
            bars.add(new MarketIntelligenceEngine.Bar(price - 1.0, price + 2.0, price - 2.0, price, 1200));
        }
        return bars;
    }

    @Test
    public void testHistoricalAnalysisExecution() {
        List<MarketIntelligenceEngine.Bar> bars = createMixedBars(100);
        HistoricalAnalysisEngine.HistoricalAnalysisResult result = historicalAnalysisEngine.analyzeHistory(bars);

        assertNotNull(result);
        assertEquals(71, result.totalCandlesEvaluated); // Index 29 to 99 = 71 candles
        assertTrue(result.buyCount > 0 || result.sellCount > 0 || result.holdCount > 0);
        assertTrue(result.averageConfidence > 0);
        assertNotNull(result.arabicSummary);
        assertTrue(result.arabicSummary.contains("ملخص نتائج التحليل التاريخي"));
    }

    @Test
    public void testHistoricalAnalysisInsufficientData() {
        List<MarketIntelligenceEngine.Bar> bars = createMixedBars(20); // < 30 bars
        HistoricalAnalysisEngine.HistoricalAnalysisResult result = historicalAnalysisEngine.analyzeHistory(bars);

        assertNotNull(result);
        assertEquals(0, result.totalCandlesEvaluated);
        assertEquals(0, result.totalSignalsGenerated);
        assertTrue(result.arabicSummary.contains("غير كافٍ"));
    }

    @Test
    public void testHistoricalAnalysisZeroLookAheadBiasGuarantee() {
        List<MarketIntelligenceEngine.Bar> bars = createMixedBars(80);

        HistoricalAnalysisEngine.HistoricalAnalysisResult res1 = historicalAnalysisEngine.analyzeHistory(bars);

        // Verify that in signalHistory, each signal at candle index `i` has parameters
        // identical to calling generateSignalAtCandle(bars, i) directly.
        SignalEngine signalEngine = new SignalEngine();
        for (HistoricalAnalysisEngine.HistoricalSignalRecord rec : res1.signalHistory) {
            SignalEngine.SignalResult singleRes = signalEngine.generateSignalAtCandle(bars, rec.candleIndex);
            assertEquals(rec.signalType, singleRes.signalType);
            assertEquals(rec.entryPrice, singleRes.entryPrice, 0.0001);
            assertEquals(rec.stopLoss, singleRes.suggestedStopLoss, 0.0001);
            assertEquals(rec.takeProfit, singleRes.suggestedTakeProfit, 0.0001);
            assertEquals(rec.confidence, singleRes.confidence, 0.0001);
        }
    }
}
