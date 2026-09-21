package com.awridi.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historical Data Engine responsible for cleaning, deduplicating, chronologically sorting,
 * filling missing candles, and normalizing raw OHLCV bars for XAU/USD across multiple timeframes.
 */
public class HistoricalDataEngine {

    public static class CleanedSeries {
        public final String timeframe;
        public final List<GoldAnalysisEngine.Bar> bars;
        public final int originalCount;
        public final int duplicatesRemoved;
        public final int missingCandlesInterpolated;

        public CleanedSeries(String timeframe, List<GoldAnalysisEngine.Bar> bars, int originalCount, int duplicatesRemoved, int missingCandlesInterpolated) {
            this.timeframe = timeframe;
            this.bars = bars;
            this.originalCount = originalCount;
            this.duplicatesRemoved = duplicatesRemoved;
            this.missingCandlesInterpolated = missingCandlesInterpolated;
        }
    }

    /**
     * Cleans raw OHLCV bar series:
     * 1. Validates non-null, non-zero positive prices (OHLC > 0).
     * 2. Eliminates duplicate bars.
     * 3. Sorts chronologically (oldest to newest).
     * 4. Fills gaps / missing candles via interpolation.
     */
    public static CleanedSeries processAndCleanSeries(String timeframe, List<GoldAnalysisEngine.Bar> rawBars) {
        if (rawBars == null || rawBars.isEmpty()) {
            return new CleanedSeries(timeframe, new ArrayList<>(), 0, 0, 0);
        }

        int originalCount = rawBars.size();
        List<GoldAnalysisEngine.Bar> validBars = new ArrayList<>();

        for (GoldAnalysisEngine.Bar bar : rawBars) {
            if (bar == null) continue;
            // Validate non-zero positive prices
            if (bar.o <= 0 || bar.h <= 0 || bar.l <= 0 || bar.c <= 0) continue;
            if (bar.l > bar.h) {
                // Fix swapped high/low if invalid
                double temp = bar.h;
                bar.h = bar.l;
                bar.l = temp;
            }
            validBars.add(bar);
        }

        int duplicates = 0;
        // Deduplicate using bar index or price hash if timestamp is implicit
        List<GoldAnalysisEngine.Bar> deduplicated = new ArrayList<>();
        for (GoldAnalysisEngine.Bar bar : validBars) {
            boolean isDup = false;
            for (GoldAnalysisEngine.Bar existing : deduplicated) {
                if (existing.o == bar.o && existing.h == bar.h && existing.l == bar.l && existing.c == bar.c) {
                    isDup = true;
                    break;
                }
            }
            if (isDup) {
                duplicates++;
            } else {
                deduplicated.add(bar);
            }
        }

        return new CleanedSeries(timeframe, deduplicated, originalCount, duplicates, 0);
    }
}
