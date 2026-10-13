package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Phase 8: Historical Data Provider
 * Provides historical bars for XAU/USD. Simulated bars are explicitly tagged as "MOCK DATA".
 */
public class HistoricalDataProvider {

    public static class HistoricalDataBatch {
        public String symbol = MainActivity.GOLD_SYMBOL;
        public String timeframe = "15min";
        public boolean isMock = true;
        public String dataLabel = "MOCK DATA";
        public List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();

        public HistoricalDataBatch() {}

        public HistoricalDataBatch(String symbol, String timeframe, boolean isMock, String dataLabel, List<MarketIntelligenceEngine.Bar> bars) {
            this.symbol = symbol != null ? symbol : MainActivity.GOLD_SYMBOL;
            this.timeframe = timeframe != null ? timeframe : "15min";
            this.isMock = isMock;
            this.dataLabel = dataLabel != null ? dataLabel : (isMock ? "MOCK DATA" : "REAL DATA");
            this.bars = bars != null ? bars : new ArrayList<>();
        }
    }

    /**
     * Generates a batch of simulated historical bars for testing purposes, clearly marked as MOCK DATA.
     */
    public static HistoricalDataBatch generateMockBars(String symbol, String timeframe, int count, double startPrice, long seed) {
        List<MarketIntelligenceEngine.Bar> list = new ArrayList<>();
        double price = startPrice > 0 ? startPrice : 2650.0;
        Random rnd = new Random(seed > 0 ? seed : 42);

        for (int i = 0; i < count; i++) {
            double change = (rnd.nextDouble() - 0.48) * 4.0;
            double open = price;
            double close = open + change;
            double high = Math.max(open, close) + (rnd.nextDouble() * 2.5);
            double low = Math.min(open, close) - (rnd.nextDouble() * 2.5);
            double vol = 1000 + rnd.nextInt(5000);
            list.add(new MarketIntelligenceEngine.Bar(open, high, low, close, vol));
            price = close;
        }

        return new HistoricalDataBatch(symbol, timeframe, true, "MOCK DATA (Simulated XAU/USD)", list);
    }

    /**
     * Converts a list of GoldAnalysisEngine.Bar to HistoricalDataBatch.
     */
    public static HistoricalDataBatch fromGoldBars(List<GoldAnalysisEngine.Bar> goldBars, String symbol, String timeframe, boolean isMock) {
        List<MarketIntelligenceEngine.Bar> miBars = new ArrayList<>();
        if (goldBars != null) {
            for (GoldAnalysisEngine.Bar gb : goldBars) {
                miBars.add(new MarketIntelligenceEngine.Bar(gb.o, gb.h, gb.l, gb.c, gb.v));
            }
        }
        String label = isMock ? "MOCK DATA (Fallback Generated)" : "REAL DATA (Twelve Data API)";
        return new HistoricalDataBatch(symbol, timeframe, isMock, label, miBars);
    }
}
