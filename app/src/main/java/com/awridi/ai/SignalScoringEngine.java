package com.awridi.ai;

import java.util.List;

public class SignalScoringEngine {

    public static class ScoreResult {
        public double bullishScore = 0.0;
        public double bearishScore = 0.0;
        public double totalScore = 0.0;
        public double confidencePct = 0.0;
        public TradingDecisionResult.SignalQuality quality = TradingDecisionResult.SignalQuality.INVALID;
    }

    public ScoreResult computeScores(TradingDecisionResult res) {
        ScoreResult scoreRes = new ScoreResult();

        if (res == null || res.decision == TradingDecisionResult.Decision.NO_TRADE || res.currentPrice <= 0) {
            scoreRes.quality = TradingDecisionResult.SignalQuality.INVALID;
            scoreRes.confidencePct = 0.0;
            return scoreRes;
        }

        double bullishPoints = 0.0;
        double bearishPoints = 0.0;
        double maxPoints = 100.0;

        // 1. Trend Evaluation (Max 35 pts)
        if (res.currentPrice > res.ema50) bullishPoints += 10.0;
        else bearishPoints += 10.0;

        if (res.ema20 > res.ema50) bullishPoints += 15.0;
        else bearishPoints += 15.0;

        if (res.currentPrice > res.ema200) bullishPoints += 10.0;
        else bearishPoints += 10.0;

        // 2. Momentum Evaluation (Max 35 pts)
        if (res.macdHist > 0) bullishPoints += 15.0;
        else if (res.macdHist < 0) bearishPoints += 15.0;

        if (res.rsi >= 45 && res.rsi <= 68) {
            bullishPoints += 20.0;
        } else if (res.rsi <= 55 && res.rsi >= 32) {
            bearishPoints += 20.0;
        } else if (res.rsi >= 70) {
            // Overbought penalty for bullish
            bearishPoints += 10.0;
        } else if (res.rsi <= 30) {
            // Oversold penalty for bearish
            bullishPoints += 10.0;
        }

        // 3. Price Structure Evaluation (Max 20 pts)
        if (res.priceStructure.contains("اختراق مقاومة") || res.priceStructure.contains("رفض هبوطي") || res.priceStructure.contains("قمم وقيعان أعلى")) {
            bullishPoints += 20.0;
        } else if (res.priceStructure.contains("كسر دعم") || res.priceStructure.contains("رفض صعودي") || res.priceStructure.contains("قمم وقيعان أدنى")) {
            bearishPoints += 20.0;
        } else {
            bullishPoints += 5.0;
            bearishPoints += 5.0;
        }

        // 4. Volatility Evaluation (Max 10 pts)
        if (res.atrValue < 4.5 && res.atrValue >= 1.5) {
            if (res.direction == TradingDecisionResult.Direction.BULLISH) bullishPoints += 10.0;
            else if (res.direction == TradingDecisionResult.Direction.BEARISH) bearishPoints += 10.0;
            else {
                bullishPoints += 5.0;
                bearishPoints += 5.0;
            }
        } else if (res.atrValue >= 4.5) {
            // Extreme volatility penalty
            bullishPoints = Math.max(0, bullishPoints - 15.0);
            bearishPoints = Math.max(0, bearishPoints - 15.0);
        }

        scoreRes.bullishScore = Math.min(100.0, Math.max(0.0, bullishPoints));
        scoreRes.bearishScore = Math.min(100.0, Math.max(0.0, bearishPoints));
        scoreRes.totalScore = scoreRes.bullishScore - scoreRes.bearishScore;

        // Confidence % calculation representing indicator alignment agreement
        double activeDominantScore = Math.max(scoreRes.bullishScore, scoreRes.bearishScore);
        double conflictingScore = Math.min(scoreRes.bullishScore, scoreRes.bearishScore);

        // Confidence % drops if conflicting score is high or decision is WAIT/NO_TRADE
        if (res.decision == TradingDecisionResult.Decision.WAIT) {
            scoreRes.confidencePct = Math.min(50.0, Math.max(10.0, 50.0 - (conflictingScore * 0.3)));
        } else if (res.decision == TradingDecisionResult.Decision.NO_TRADE) {
            scoreRes.confidencePct = 0.0;
        } else {
            scoreRes.confidencePct = Math.min(98.0, Math.max(0.0, activeDominantScore - (conflictingScore * 0.5)));
        }

        // Determine Signal Quality
        if (res.decision == TradingDecisionResult.Decision.NO_TRADE || res.atrValue >= 4.5) {
            scoreRes.quality = TradingDecisionResult.SignalQuality.INVALID;
        } else if (res.decision == TradingDecisionResult.Decision.WAIT) {
            scoreRes.quality = TradingDecisionResult.SignalQuality.LOW;
        } else if (scoreRes.confidencePct >= 75.0 && activeDominantScore >= 70.0) {
            scoreRes.quality = TradingDecisionResult.SignalQuality.HIGH;
        } else if (scoreRes.confidencePct >= 55.0 && activeDominantScore >= 50.0) {
            scoreRes.quality = TradingDecisionResult.SignalQuality.MEDIUM;
        } else {
            scoreRes.quality = TradingDecisionResult.SignalQuality.LOW;
        }

        return scoreRes;
    }

    public ScoreResult computeScoresAtCandle(List<MarketIntelligenceEngine.Bar> bars, int endIndex) {
        TradingDecisionEngine decisionEngine = new TradingDecisionEngine();
        TradingDecisionResult res = decisionEngine.evaluateAtCandle(bars, endIndex);
        return computeScores(res);
    }
}
