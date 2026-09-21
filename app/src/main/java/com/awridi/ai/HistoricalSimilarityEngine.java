package com.awridi.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Historical Similarity Engine responsible for searching historical bar datasets for periods
 * exhibiting strong feature vector resemblance to the current market state.
 * Prevents look-ahead bias by evaluating historical outcomes strictly forward from match points.
 */
public class HistoricalSimilarityEngine {

    public static class SimilarityMatch {
        public final int historicalIndex;
        public final double similarityScore; // 0.0 to 1.0 (1.0 = identical)
        public final MarketStateEngine.Regime historicalRegime;
        public final double priceAfter5Bars;
        public final double priceAfter10Bars;
        public final double priceAfter20Bars;
        public final double mfe20; // Maximum Favorable Excursion (highest gain over 20 bars)
        public final double mae20; // Maximum Adverse Excursion (highest drawdown over 20 bars)
        public final boolean targetHitBeforeStop; // Evaluated against 1.5x ATR target and SL

        public SimilarityMatch(int historicalIndex, double similarityScore, MarketStateEngine.Regime historicalRegime,
                               double priceAfter5Bars, double priceAfter10Bars, double priceAfter20Bars,
                               double mfe20, double mae20, boolean targetHitBeforeStop) {
            this.historicalIndex = historicalIndex;
            this.similarityScore = similarityScore;
            this.historicalRegime = historicalRegime;
            this.priceAfter5Bars = priceAfter5Bars;
            this.priceAfter10Bars = priceAfter10Bars;
            this.priceAfter20Bars = priceAfter20Bars;
            this.mfe20 = mfe20;
            this.mae20 = mae20;
            this.targetHitBeforeStop = targetHitBeforeStop;
        }
    }

    public static class SimilarityReport {
        public final int sampleSize;
        public final List<SimilarityMatch> matches;
        public final double averageSimilarityScore;
        public final double empiricalBullishOutcomePct; // % of matches where price rose over 20 bars
        public final double averageMFE;
        public final double averageMAE;
        public final int targetHitCount;

        public SimilarityReport(int sampleSize, List<SimilarityMatch> matches, double averageSimilarityScore,
                                double empiricalBullishOutcomePct, double averageMFE, double averageMAE, int targetHitCount) {
            this.sampleSize = sampleSize;
            this.matches = matches;
            this.averageSimilarityScore = averageSimilarityScore;
            this.empiricalBullishOutcomePct = empiricalBullishOutcomePct;
            this.averageMFE = averageMFE;
            this.averageMAE = averageMAE;
            this.targetHitCount = targetHitCount;
        }
    }

    public static SimilarityReport findSimilarHistoricalContexts(List<GoldAnalysisEngine.Bar> bars, int currentIdx, int topN) {
        if (bars == null || bars.size() < 50 || currentIdx < 30 || currentIdx >= bars.size()) {
            return new SimilarityReport(0, new ArrayList<>(), 0, 0, 0, 0, 0);
        }

        FeatureEngine.FeatureVector currentFeatures = FeatureEngine.extractFeatures(bars, currentIdx);
        List<SimilarityMatch> candidates = new ArrayList<>();

        // Prevent look-ahead bias: search strictly up to currentIdx - 25 (allowing 20 bars forward evaluation)
        int searchEnd = currentIdx - 25;

        for (int i = 30; i <= searchEnd; i++) {
            FeatureEngine.FeatureVector histFeatures = FeatureEngine.extractFeatures(bars, i);

            double dist = computeNormalizedEuclideanDistance(currentFeatures, histFeatures);
            double simScore = Math.max(0.0, 1.0 - (dist / 10.0));

            if (simScore >= 0.60) {
                // Evaluate 20 bars forward outcome
                GoldAnalysisEngine.Bar matchBar = bars.get(i);
                double basePrice = matchBar.c;

                double p5 = (i + 5 < bars.size()) ? bars.get(i + 5).c : basePrice;
                double p10 = (i + 10 < bars.size()) ? bars.get(i + 10).c : basePrice;
                double p20 = (i + 20 < bars.size()) ? bars.get(i + 20).c : basePrice;

                double maxGain = 0;
                double maxDrawdown = 0;
                double atr = Math.max(1.0, histFeatures.atr14);
                double targetPrice = basePrice + (1.5 * atr);
                double stopPrice = basePrice - (1.5 * atr);

                boolean targetHitFirst = false;
                boolean stopHitFirst = false;

                for (int f = 1; f <= 20 && (i + f) < bars.size(); f++) {
                    GoldAnalysisEngine.Bar fBar = bars.get(i + f);
                    double gain = fBar.h - basePrice;
                    double drawdown = basePrice - fBar.l;

                    if (gain > maxGain) maxGain = gain;
                    if (drawdown > maxDrawdown) maxDrawdown = drawdown;

                    if (!targetHitFirst && !stopHitFirst) {
                        if (fBar.h >= targetPrice) targetHitFirst = true;
                        else if (fBar.l <= stopPrice) stopHitFirst = true;
                    }
                }

                MarketStateEngine.MarketState histState = MarketStateEngine.evaluateMarketState(bars.subList(0, i + 1), histFeatures);
                candidates.add(new SimilarityMatch(i, simScore, histState.primaryRegime, p5, p10, p20, maxGain, maxDrawdown, targetHitFirst));
            }
        }

        // Sort descending by similarity score
        Collections.sort(candidates, (a, b) -> Double.compare(b.similarityScore, a.similarityScore));

        List<SimilarityMatch> topMatches = candidates.subList(0, Math.min(topN, candidates.size()));
        if (topMatches.isEmpty()) {
            return new SimilarityReport(0, new ArrayList<>(), 0, 0, 0, 0, 0);
        }

        double simSum = 0;
        double mfeSum = 0;
        double maeSum = 0;
        int bullishCount = 0;
        int targetHits = 0;

        for (SimilarityMatch m : topMatches) {
            simSum += m.similarityScore;
            mfeSum += m.mfe20;
            maeSum += m.mae20;
            if (m.priceAfter20Bars > bars.get(m.historicalIndex).c) bullishCount++;
            if (m.targetHitBeforeStop) targetHits++;
        }

        int size = topMatches.size();
        double avgSim = simSum / size;
        double avgMFE = mfeSum / size;
        double avgMAE = maeSum / size;
        double bullPct = ((double) bullishCount / size) * 100.0;

        return new SimilarityReport(size, topMatches, avgSim, bullPct, avgMFE, avgMAE, targetHits);
    }

    private static double computeNormalizedEuclideanDistance(FeatureEngine.FeatureVector a, FeatureEngine.FeatureVector b) {
        double dRsi = (a.rsi - b.rsi) / 100.0;
        double dBbPos = a.bbPosition - b.bbPosition;
        double dAdx = (a.adx - b.adx) / 100.0;
        double dPct = (a.pctChange - b.pctChange) / 10.0;
        double dMacdHist = (a.macdHist - b.macdHist) / 5.0;

        return Math.sqrt((dRsi * dRsi) + (dBbPos * dBbPos) + (dAdx * dAdx) + (dPct * dPct) + (dMacdHist * dMacdHist));
    }
}
