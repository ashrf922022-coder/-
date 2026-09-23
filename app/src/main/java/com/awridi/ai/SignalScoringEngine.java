package com.awridi.ai;

import java.util.List;
import java.util.Locale;

/**
 * SignalScoringEngine - Evaluates signal strength, bullish/bearish scores, confidence %,
 * and signal quality (HIGH, MEDIUM, LOW, INVALID) based strictly on current and historical data.
 */
public class SignalScoringEngine {

    public static void evaluateScore(TradingDecisionResult res) {
        if (res == null) return;

        res.supportingFactors.clear();
        res.conflictingFactors.clear();

        // 1. Check for Invalid or Insufficient Data or High Volatility Safety Halt
        if (res.decision == TradingDecisionResult.Decision.NO_TRADE &&
                (res.arabicExplanation.contains("غير كافية") || res.arabicExplanation.contains("غير صالحة"))) {
            res.bullishScore = 0;
            res.bearishScore = 0;
            res.totalScore = 0;
            res.confidence = 0.0;
            res.signalQuality = "INVALID";
            res.direction = TradingDecisionResult.Direction.UNKNOWN;
            return;
        }

        int bullishScore = 0;
        int bearishScore = 0;

        // Factor 1: Price vs EMA50
        if (res.currentPrice > res.ema50) {
            bullishScore++;
            res.supportingFactors.add("السعر أعلى من المتوسط المتحرك EMA50 ($" + String.format(Locale.US, "%.2f", res.ema50) + ")");
        } else if (res.currentPrice < res.ema50) {
            bearishScore++;
            res.conflictingFactors.add("السعر أسفل من المتوسط المتحرك EMA50 ($" + String.format(Locale.US, "%.2f", res.ema50) + ")");
        }

        // Factor 2: EMA Alignment (EMA20 vs EMA50)
        if (res.ema20 > res.ema50) {
            bullishScore++;
            res.supportingFactors.add("ترتيب المتوسطات صاعد (EMA20 > EMA50)");
        } else if (res.ema20 < res.ema50) {
            bearishScore++;
            res.conflictingFactors.add("ترتيب المتوسطات هابط (EMA20 < EMA50)");
        }

        // Factor 3: Price vs Major Trend line (EMA200)
        if (res.currentPrice > res.ema200) {
            bullishScore++;
            res.supportingFactors.add("السعر أعلى من المتوسط المتحرك الرئيسي EMA200 ($" + String.format(Locale.US, "%.2f", res.ema200) + ")");
        } else if (res.currentPrice < res.ema200) {
            bearishScore++;
            res.conflictingFactors.add("السعر أسفل من المتوسط المتحرك الرئيسي EMA200 ($" + String.format(Locale.US, "%.2f", res.ema200) + ")");
        }

        // Factor 4: MACD Histogram Momentum
        if (res.macdHist > 0) {
            bullishScore++;
            res.supportingFactors.add("زخم MACD موجب وإيجابي (" + String.format(Locale.US, "%.4f", res.macdHist) + ")");
        } else if (res.macdHist < 0) {
            bearishScore++;
            res.conflictingFactors.add("زخم MACD سالب وسلبي (" + String.format(Locale.US, "%.4f", res.macdHist) + ")");
        }

        // Factor 5: RSI Momentum Range
        if (res.rsi >= 45 && res.rsi <= 68) {
            bullishScore++;
            res.supportingFactors.add("مؤشر RSI مستقر في النطاق الصاعد (" + String.format(Locale.US, "%.1f", res.rsi) + ")");
        } else if (res.rsi <= 55 && res.rsi >= 32) {
            bearishScore++;
            res.conflictingFactors.add("مؤشر RSI في النطاق الهابط (" + String.format(Locale.US, "%.1f", res.rsi) + ")");
        }

        // RSI Extreme Warnings
        if (res.rsi >= 70) {
            res.conflictingFactors.add("تحذير: مؤشر RSI في منطقة تشبع شرائي (" + String.format(Locale.US, "%.1f", res.rsi) + ")");
        } else if (res.rsi <= 30) {
            res.conflictingFactors.add("تحذير: مؤشر RSI في منطقة تشبع بيعي (" + String.format(Locale.US, "%.1f", res.rsi) + ")");
        }

        // Factor 6: Price Structure (Breakout, Rejection, Highs/Lows)
        if (res.priceStructure.contains("اختراق مقاومة") || res.priceStructure.contains("رفض هبوطي") || res.priceStructure.contains("قمم وقيعان أعلى")) {
            bullishScore++;
            res.supportingFactors.add("بنية السعر صاعدة: " + res.priceStructure);
        } else if (res.priceStructure.contains("كسر دعم") || res.priceStructure.contains("رفض صعودي") || res.priceStructure.contains("قمم وقيعان أدنى")) {
            bearishScore++;
            res.conflictingFactors.add("بنية السعر هابطة: " + res.priceStructure);
        }

        // Factor 7: Market Regime Support / Warning
        if (res.marketRegime != null && res.marketRegime.regime != null) {
            switch (res.marketRegime.regime) {
                case TREND_UP:
                    bullishScore++;
                    res.supportingFactors.add("حالة السوق الداعمة: اتجاه صاعد مؤكد (TREND_UP)");
                    break;
                case TREND_DOWN:
                    bearishScore++;
                    res.conflictingFactors.add("حالة السوق الداعمة للبيع: اتجاه هابط مؤكد (TREND_DOWN)");
                    break;
                case RANGE:
                    res.conflictingFactors.add("حالة السوق عرضية (RANGE) — تقلل جودة إشارات الاتجاه");
                    break;
                case HIGH_VOLATILITY:
                    res.conflictingFactors.add("تحذير: حالة السوق عالية التقلب (HIGH_VOLATILITY) ترفع المخاطر التشغيلية");
                    break;
                case LOW_VOLATILITY:
                    res.supportingFactors.add("حالة السوق منخفضة التقلب (LOW_VOLATILITY) — استقرار ونطاق ضيق");
                    break;
                case TRANSITION:
                case UNCERTAIN:
                    res.conflictingFactors.add("تحذير: حالة السوق في مرحلة انتقال أو عدم يقين (" + res.marketRegime.regime.name() + ") تخفض نسبة الثقة");
                    break;
            }
        }

        // Volatility Factor Warning
        if (res.atrValue >= 4.5) {
            res.conflictingFactors.add("تحذير: تقلب حاد وغير آمن في الأسواق (ATR = " + String.format(Locale.US, "%.2f", res.atrValue) + ")");
        }

        res.bullishScore = bullishScore;
        res.bearishScore = bearishScore;
        res.totalScore = bullishScore + bearishScore;

        // Volatility Safety Stop: Extreme ATR invalidates new trades
        if (res.atrValue >= 4.5) {
            res.confidence = 0.0;
            res.signalQuality = "INVALID";
            res.decision = TradingDecisionResult.Decision.NO_TRADE;
            res.direction = TradingDecisionResult.Direction.UNKNOWN;
            return;
        }

        // Calculate Confidence %
        int totalActive = bullishScore + bearishScore;
        int dominantScore = Math.max(bullishScore, bearishScore);
        int minorScore = Math.min(bullishScore, bearishScore);

        if (totalActive == 0) {
            res.confidence = 0.0;
        } else {
            res.confidence = ((double) dominantScore / totalActive) * 100.0;
        }

        // Cap confidence or penalize quality if Market Regime is RANGE or TRANSITION/UNCERTAIN
        if (res.marketRegime != null && res.marketRegime.regime != null) {
            if (res.marketRegime.regime == MarketRegimeResult.Regime.RANGE ||
                res.marketRegime.regime == MarketRegimeResult.Regime.TRANSITION ||
                res.marketRegime.regime == MarketRegimeResult.Regime.UNCERTAIN) {
                res.confidence = Math.max(0.0, res.confidence - 10.0);
            }
        }

        // Determine Decision and Signal Quality
        if (bullishScore >= 3 && minorScore <= 1 && res.rsi < 70) {
            res.direction = TradingDecisionResult.Direction.BULLISH;
            if (res.confidence >= 80.0 && bullishScore >= 4) {
                res.signalQuality = (res.marketRegime != null && res.marketRegime.regime == MarketRegimeResult.Regime.RANGE) ? "MEDIUM" : "HIGH";
                res.decision = TradingDecisionResult.Decision.BUY;
            } else if (res.confidence >= 60.0) {
                res.signalQuality = "MEDIUM";
                res.decision = TradingDecisionResult.Decision.BUY;
            } else {
                res.signalQuality = "LOW";
                res.decision = TradingDecisionResult.Decision.WAIT;
            }
        } else if (bearishScore >= 3 && minorScore <= 1 && res.rsi > 30) {
            res.direction = TradingDecisionResult.Direction.BEARISH;
            if (res.confidence >= 80.0 && bearishScore >= 4) {
                res.signalQuality = (res.marketRegime != null && res.marketRegime.regime == MarketRegimeResult.Regime.RANGE) ? "MEDIUM" : "HIGH";
                res.decision = TradingDecisionResult.Decision.SELL;
            } else if (res.confidence >= 60.0) {
                res.signalQuality = "MEDIUM";
                res.decision = TradingDecisionResult.Decision.SELL;
            } else {
                res.signalQuality = "LOW";
                res.decision = TradingDecisionResult.Decision.WAIT;
            }
        } else {
            // Contradicting factors or low score -> WAIT
            res.decision = TradingDecisionResult.Decision.WAIT;
            res.direction = TradingDecisionResult.Direction.NEUTRAL;
            if (res.confidence >= 60.0 && dominantScore >= 2) {
                res.signalQuality = "MEDIUM";
            } else {
                res.signalQuality = "LOW";
            }
        }
    }
}
