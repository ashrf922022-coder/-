package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MarketRegimeEngine - Detects the current market regime based strictly on available
 * price data up to the specified target candle index without look-ahead bias.
 */
public class MarketRegimeEngine {

    public MarketRegimeResult evaluate(List<MarketIntelligenceEngine.Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return createInvalidResult("بيانات غير متوفرة أو فارغة");
        }
        return evaluateAtCandle(bars, bars.size() - 1);
    }

    public MarketRegimeResult evaluateAtCandle(List<MarketIntelligenceEngine.Bar> bars, int endIndex) {
        if (bars == null || endIndex < 0 || endIndex >= bars.size()) {
            return createInvalidResult("مؤشر الشمعة غير صالح أو خارج النطاق");
        }

        if (endIndex < 29) {
            return createInvalidResult("بيانات غير كافية لتحليل حالة السوق (تتطلب 30 شمعة على الأقل)");
        }

        // Slice strictly up to endIndex to guarantee NO look-ahead bias
        List<MarketIntelligenceEngine.Bar> slice = new ArrayList<>(bars.subList(0, endIndex + 1));
        int n = slice.size();
        int targetIdx = n - 1;

        MarketRegimeResult result = new MarketRegimeResult();
        MarketIntelligenceEngine.Bar latest = slice.get(targetIdx);
        result.currentPrice = latest.close;

        // Calculate technical indicators using existing TradingDecisionEngine helpers
        result.ema20 = TradingDecisionEngine.calcEMA(slice, 20, targetIdx);
        result.ema50 = TradingDecisionEngine.calcEMA(slice, 50, targetIdx);
        result.ema200 = TradingDecisionEngine.calcEMA(slice, 200, targetIdx);

        result.rsi = TradingDecisionEngine.calcRSI(slice, 14, targetIdx);
        result.macdHist = TradingDecisionEngine.calcMACDHist(slice, targetIdx);
        result.atr14 = TradingDecisionEngine.calcATR(slice, 14, targetIdx);

        double prevAtr = targetIdx >= 14 ? TradingDecisionEngine.calcATR(slice, 14, targetIdx - 1) : result.atr14;
        if (prevAtr > 0) {
            result.volatilityChange = ((result.atr14 - prevAtr) / prevAtr) * 100.0;
        }

        result.support = TradingDecisionEngine.findSupport(slice, 20, targetIdx);
        result.resistance = TradingDecisionEngine.findResistance(slice, 20, targetIdx);

        result.priceStructure = analyzePriceStructure(slice, targetIdx, result.support, result.resistance, result.atr14);

        // Detect regime
        determineRegime(result, slice, targetIdx);

        // Build Arabic explanation rationale
        buildExplanation(result);

        return result;
    }

    private void determineRegime(MarketRegimeResult res, List<MarketIntelligenceEngine.Bar> slice, int targetIdx) {
        res.supportingFactors.clear();
        res.conflictingFactors.clear();

        // 1. Check for High Volatility Regime
        boolean isHighVol = res.atr14 >= 4.5 || (res.volatilityChange >= 40.0 && res.atr14 >= 3.5);
        if (isHighVol) {
            res.regime = MarketRegimeResult.Regime.HIGH_VOLATILITY;
            res.confidence = Math.min(95.0, 75.0 + (res.atr14 * 4.0));
            res.supportingFactors.add("مستوى تقلب سعري مرتفع جداً (ATR = " + String.format(Locale.US, "%.2f", res.atr14) + ")");
            if (res.volatilityChange >= 40.0) {
                res.supportingFactors.add("ارتفاع حاد ومفاجئ في معدل التقلب بنسبة (" + String.format(Locale.US, "%.1f%%", res.volatilityChange) + ")");
            }
            res.conflictingFactors.add("مخاطر انزلاق سعري واتساع الفوارق السعرية (Slippage & Spread Expansion)");
            return;
        }

        // 2. Check for Low Volatility Regime
        boolean isLowVol = res.atr14 < 1.0 && Math.abs(res.ema20 - res.ema50) < 0.3 && Math.abs(res.macdHist) < 0.2;
        if (isLowVol) {
            res.regime = MarketRegimeResult.Regime.LOW_VOLATILITY;
            res.confidence = Math.min(90.0, 70.0 + ((1.0 - res.atr14) * 20.0));
            res.supportingFactors.add("مستوى تقلب ضعيف ونطاق حركة ضيق (ATR = " + String.format(Locale.US, "%.2f", res.atr14) + ")");
            res.supportingFactors.add("تقارب شديد بين المتوسطات المتحركة (EMA20 & EMA50)");
            res.conflictingFactors.add("ضعف الزخم والتداول في حالة خمول قبل انفجار سعري محتمل");
            return;
        }

        // 3. Price Structure Flags
        boolean bullishStructure = res.priceStructure.contains("قمم وقيعان أعلى") || res.priceStructure.contains("اختراق مقاومة") || res.priceStructure.contains("رفض هبوطي");
        boolean bearishStructure = res.priceStructure.contains("قمم وقيعان أدنى") || res.priceStructure.contains("كسر دعم") || res.priceStructure.contains("رفض صعودي");

        // 4. RANGE Check: EMAs close together, price bounded between support/resistance, RSI balanced
        boolean emasTangled = Math.abs(res.ema20 - res.ema50) <= (res.atr14 * 0.7);
        boolean priceInBand = res.currentPrice <= res.resistance && res.currentPrice >= res.support;
        boolean rsiNeutral = res.rsi >= 38.0 && res.rsi <= 62.0;

        if (emasTangled && priceInBand && rsiNeutral && !res.priceStructure.contains("اختراق") && !res.priceStructure.contains("كسر")) {
            res.regime = MarketRegimeResult.Regime.RANGE;
            res.confidence = 80.0;
            res.supportingFactors.add("السوق يتحرك في نطاق عرضي متوازن بين الدعم ($" + String.format(Locale.US, "%.2f", res.support) + ") والمقاومة ($" + String.format(Locale.US, "%.2f", res.resistance) + ")");
            res.supportingFactors.add("تداخل وتقارب المتوسطات المتحركة EMA20 و EMA50");
            res.conflictingFactors.add("غياب الاتجاه الواضح يقلل فاعلية مؤشرات تتبع الاتجاه");
            return;
        }

        // 5. TRANSITION Check: Conflict between Price Position vs EMA Reversal (e.g., Price > EMA200 but sharp drop EMA20 < EMA50 & negative MACD)
        boolean trendDivergence = (res.currentPrice > res.ema200 && res.ema20 < res.ema50 && res.macdHist < 0) ||
                                 (res.currentPrice < res.ema200 && res.ema20 > res.ema50 && res.macdHist > 0);

        if (trendDivergence || (res.rsi > 72 && bearishStructure) || (res.rsi < 28 && bullishStructure)) {
            res.regime = MarketRegimeResult.Regime.TRANSITION;
            res.confidence = 70.0;
            res.supportingFactors.add("السوق في مرحلة انتقال / تصحيح اتجاه حاد بين القوى الشرائية والبيعية");
            res.supportingFactors.add("تعارض بين موضع السعر بالنسبة لـ EMA200 وتقاطع المتوسطات القصيرة EMA20/EMA50");
            res.conflictingFactors.add("تضارب إشارات الزخم والاتجاه يتطلب الحذر ومراقبة الاختراق");
            return;
        }

        // 6. TREND_UP Check
        boolean bullishEMAs = res.currentPrice > res.ema200 && res.ema20 > res.ema50 && res.ema50 > res.ema200;
        boolean weakBullishEMAs = res.currentPrice > res.ema50 && res.ema20 > res.ema50;

        if (bullishEMAs || (weakBullishEMAs && res.macdHist > 0 && res.rsi >= 48)) {
            res.regime = MarketRegimeResult.Regime.TREND_UP;
            res.confidence = bullishEMAs ? 90.0 : 75.0;
            res.supportingFactors.add("اتجاه صاعد منتظم مع ترتيب إيجابي للمتوسطات المتحركة");
            res.supportingFactors.add("السعر أعلى من المتوسط الرئيسي EMA200 ($" + String.format(Locale.US, "%.2f", res.ema200) + ")");
            if (res.macdHist > 0) res.supportingFactors.add("زخم صعودي موجب على مؤشر MACD");
            if (bullishStructure) res.supportingFactors.add("بنية سعرية صاعدة: " + res.priceStructure);

            if (res.rsi > 68) res.conflictingFactors.add("تحذير من اقتراب RSI من منطقة التشبع الشرائي");
            return;
        }

        // 7. TREND_DOWN Check
        boolean bearishEMAs = res.currentPrice < res.ema200 && res.ema20 < res.ema50 && res.ema50 < res.ema200;
        boolean weakBearishEMAs = res.currentPrice < res.ema50 && res.ema20 < res.ema50;

        if (bearishEMAs || (weakBearishEMAs && res.macdHist < 0 && res.rsi <= 52)) {
            res.regime = MarketRegimeResult.Regime.TREND_DOWN;
            res.confidence = bearishEMAs ? 90.0 : 75.0;
            res.supportingFactors.add("اتجاه هابط ضاغط مع ترتيب سلبي للمتوسطات المتحركة");
            res.supportingFactors.add("السعر أسفل المتوسط الرئيسي EMA200 ($" + String.format(Locale.US, "%.2f", res.ema200) + ")");
            if (res.macdHist < 0) res.supportingFactors.add("زخم هبوطي سالب على مؤشر MACD");
            if (bearishStructure) res.supportingFactors.add("بنية سعرية هابطة: " + res.priceStructure);

            if (res.rsi < 32) res.conflictingFactors.add("تحذير من اقتراب RSI من منطقة التشبع البيعي");
            return;
        }

        // 8. Default UNCERTAIN
        res.regime = MarketRegimeResult.Regime.UNCERTAIN;
        res.confidence = 45.0;
        res.conflictingFactors.add("تضارب الإشارات الفنية وعدم اتساق بنية السعر مع المتوسطات");
        res.conflictingFactors.add("عدم يقين مرتفع في الرؤية الفنية لاتجاه الذهب");
    }

    private String analyzePriceStructure(List<MarketIntelligenceEngine.Bar> bars, int end, double support, double resistance, double atr) {
        if (end < 3) return "غير محدد";

        MarketIntelligenceEngine.Bar current = bars.get(end);
        MarketIntelligenceEngine.Bar prev = bars.get(end - 1);

        if (current.close > resistance && prev.close <= resistance) {
            return "اختراق مقاومة صعودي (Breakout)";
        }
        if (current.close < support && prev.close >= support) {
            return "كسر دعم هبوطي (Breakout)";
        }

        double upperWick = current.high - Math.max(current.open, current.close);
        double lowerWick = Math.min(current.open, current.close) - current.low;
        double candleBody = Math.abs(current.close - current.open);

        if (lowerWick > candleBody * 2 && current.low <= support + (atr * 0.3)) {
            return "رفض هبوطي عند الدعم (Bullish Rejection)";
        }
        if (upperWick > candleBody * 2 && current.high >= resistance - (atr * 0.3)) {
            return "رفض صعودي عند المقاومة (Bearish Rejection)";
        }

        MarketIntelligenceEngine.Bar barMinus2 = bars.get(end - 2);
        if (current.high > prev.high && prev.high > barMinus2.high && current.low > prev.low && prev.low > barMinus2.low) {
            return "قمم وقيعان أعلى (Higher High / Higher Low)";
        }
        if (current.high < prev.high && prev.high < barMinus2.high && current.low < prev.low && prev.low < barMinus2.low) {
            return "قمم وقيعان أدنى (Lower High / Lower Low)";
        }

        return "حركة داخلية في النطاق";
    }

    private MarketRegimeResult createInvalidResult(String reasonArabic) {
        MarketRegimeResult result = new MarketRegimeResult();
        result.regime = MarketRegimeResult.Regime.UNCERTAIN;
        result.confidence = 0.0;
        result.conflictingFactors.add(reasonArabic);
        result.explanation = "حالة السوق غير محددة: " + reasonArabic;
        return result;
    }

    private void buildExplanation(MarketRegimeResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("• نظام حالة السوق (Market Regime): ").append(res.regime.name()).append("\n");
        sb.append("• درجة الثقة: ").append(String.format(Locale.US, "%.1f%%", res.confidence)).append("\n");
        sb.append("• السعر الحالي: $").append(String.format(Locale.US, "%.2f", res.currentPrice)).append("\n");
        sb.append("• تقلب الأسواق (ATR14): $").append(String.format(Locale.US, "%.2f", res.atr14)).append("\n");

        if (!res.supportingFactors.isEmpty()) {
            sb.append("\nعوامل تدعم حالة السوق:\n");
            for (String factor : res.supportingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        if (!res.conflictingFactors.isEmpty()) {
            sb.append("\nعوامل التعارض / المخاطر:\n");
            for (String factor : res.conflictingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        res.explanation = sb.toString();
    }
}
