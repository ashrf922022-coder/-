package com.awridi.ai;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EngineUnitTest {

    private List<GoldAnalysisEngine.Bar> mockBars;

    @Before
    public void setUp() {
        mockBars = new ArrayList<>();
        double basePrice = 2600.0;
        for (int i = 0; i < 100; i++) {
            double offset = (i % 2 == 0) ? 1.5 : -1.0;
            double open = basePrice + (i * 0.5);
            double close = open + offset;
            double high = Math.max(open, close) + 2.0;
            double low = Math.min(open, close) - 2.0;
            mockBars.add(new GoldAnalysisEngine.Bar(open, high, low, close, 1000 + i));
        }
    }

    @Test
    public void testHistoricalDataEngineCleaning() {
        List<GoldAnalysisEngine.Bar> rawWithDups = new ArrayList<>(mockBars);
        rawWithDups.add(mockBars.get(0)); // Add explicit duplicate
        rawWithDups.add(new GoldAnalysisEngine.Bar(-10, 0, 0, 0, 0)); // Add invalid bar

        HistoricalDataEngine.CleanedSeries cleaned = HistoricalDataEngine.processAndCleanSeries("15m", rawWithDups);
        assertNotNull(cleaned);
        assertTrue(cleaned.duplicatesRemoved >= 1);
        assertFalse(cleaned.bars.isEmpty());
    }

    @Test
    public void testFeatureEngineExtraction() {
        FeatureEngine.FeatureVector features = FeatureEngine.extractFeatures(mockBars, mockBars.size() - 1);
        assertNotNull(features);
        assertTrue(features.rsi >= 0 && features.rsi <= 100);
        assertTrue(features.atr14 > 0);
        assertTrue(features.ema20 > 0);
    }

    @Test
    public void testMarketStateEngineEvaluation() {
        FeatureEngine.FeatureVector features = FeatureEngine.extractFeatures(mockBars, mockBars.size() - 1);
        MarketStateEngine.MarketState state = MarketStateEngine.evaluateMarketState(mockBars, features);
        assertNotNull(state);
        assertNotNull(state.primaryRegime);
    }

    @Test
    public void testHistoricalSimilarityEngine() {
        HistoricalSimilarityEngine.SimilarityReport report = HistoricalSimilarityEngine.findSimilarHistoricalContexts(mockBars, mockBars.size() - 1, 10);
        assertNotNull(report);
        assertTrue(report.sampleSize >= 0);
    }

    @Test
    public void testWalkForwardEngine() {
        WalkForwardEngine.WalkForwardReport report = WalkForwardEngine.runWalkForwardAnalysis(mockBars, null);
        assertNotNull(report);
        assertTrue(report.totalWindows >= 0);
    }

    @Test
    public void testRiskIntelligenceEngine() {
        RiskIntelligenceEngine.RiskCheckResult risk = RiskIntelligenceEngine.evaluateRisk(10000, 1.0, 5.0, 0, 2600, 2580);
        assertNotNull(risk);
        assertTrue(risk.isTradeAllowed);
        assertTrue(risk.allowedLotSize > 0);
    }

    @Test
    public void testMarketIntelligenceBot() {
        Map<String, List<GoldAnalysisEngine.Bar>> mtf = new HashMap<>();
        mtf.put("15min", mockBars);
        mtf.put("1h", mockBars);

        MarketIntelligenceBot.MarketIntelligenceReport report = MarketIntelligenceBot.generateIntelligence(mtf, null);
        assertNotNull(report);
        assertNotNull(report.decision);
        assertTrue(report.fullArabicSummary.contains("XAU/USD"));
    }
}
