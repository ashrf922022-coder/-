package com.awridi.ai;

import java.util.List;

/**
 * Pattern Intelligence Engine identifying price patterns (breakouts, pullbacks, rejections)
 * in historical bar series.
 */
public class PatternIntelligenceEngine {

    public enum PatternType {
        BULLISH_BREAKOUT,
        BEARISH_BREAKOUT,
        SUPPORT_BOUNCE,
        RESISTANCE_REJECTION,
        BULLISH_ENGULFIG,
        BEARISH_ENGULFING,
        NO_PATTERN
    }

    public static class PatternResult {
        public final PatternType pattern;
        public final String arabicDescription;
        public final double patternConfidence;

        public PatternResult(PatternType pattern, String arabicDescription, double patternConfidence) {
            this.pattern = pattern;
            this.arabicDescription = arabicDescription;
            this.patternConfidence = patternConfidence;
        }
    }

    public static PatternResult detectPattern(List<GoldAnalysisEngine.Bar> bars, FeatureEngine.FeatureVector features) {
        if (bars == null || bars.size() < 3 || features == null) {
            return new PatternResult(PatternType.NO_PATTERN, "لا يوجد نمط واضح", 0);
        }

        int n = bars.size();
        GoldAnalysisEngine.Bar cur = bars.get(n - 1);
        GoldAnalysisEngine.Bar prev = bars.get(n - 2);

        // Engulfing patterns
        boolean isBullishEngulfing = prev.c < prev.o && cur.c > cur.o && cur.c > prev.o && cur.o < prev.c;
        boolean isBearishEngulfing = prev.c > prev.o && cur.c < cur.o && cur.c < prev.c && cur.o > prev.o;

        if (cur.c > features.bbUpper && cur.c > prev.h) {
            return new PatternResult(PatternType.BULLISH_BREAKOUT, "اختراق صاعد لمستويات المقاومة 🟢", 0.85);
        } else if (cur.c < features.bbLower && cur.c < prev.l) {
            return new PatternResult(PatternType.BEARISH_BREAKOUT, "كسر هابط لمستويات الدعم 🔴", 0.85);
        } else if (cur.l <= features.bbLower && cur.c > features.bbLower && features.lowerWick > (features.candleBody * 1.5)) {
            return new PatternResult(PatternType.SUPPORT_BOUNCE, "ارتداد صاعد من مستوى الدعم السفلي 🟢", 0.80);
        } else if (cur.h >= features.bbUpper && cur.c < features.bbUpper && features.upperWick > (features.candleBody * 1.5)) {
            return new PatternResult(PatternType.RESISTANCE_REJECTION, "رفض وهبوط من مستوى المقاومة العلوي 🔴", 0.80);
        } else if (isBullishEngulfing) {
            return new PatternResult(PatternType.BULLISH_ENGULFIG, "نمط ابتلاع شرائي صاعد (Bullish Engulfing) 🟢", 0.75);
        } else if (isBearishEngulfing) {
            return new PatternResult(PatternType.BEARISH_ENGULFING, "نمط ابتلاع بيعي هابط (Bearish Engulfing) 🔴", 0.75);
        }

        return new PatternResult(PatternType.NO_PATTERN, "لا يوجد نمط نموذجي حالياً 🟡", 0.50);
    }
}
