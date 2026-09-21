package com.awridi.ai;

import java.util.List;
import java.util.Map;

/**
 * Multi-Timeframe Alignment Engine correlating macro trends (1D/4H) with structural setup (1H)
 * and execution signals (15M/5M).
 */
public class MultiTimeframeEngine {

    public static class MTFAlignment {
        public String dailyTrend;
        public String fourHourTrend;
        public String oneHourStructure;
        public String fifteenMinSetup;
        public boolean isFullyAligned;
        public String alignmentSummary;

        public MTFAlignment(String dailyTrend, String fourHourTrend, String oneHourStructure, String fifteenMinSetup, boolean isFullyAligned, String alignmentSummary) {
            this.dailyTrend = dailyTrend;
            this.fourHourTrend = fourHourTrend;
            this.oneHourStructure = oneHourStructure;
            this.fifteenMinSetup = fifteenMinSetup;
            this.isFullyAligned = isFullyAligned;
            this.alignmentSummary = alignmentSummary;
        }
    }

    public static MTFAlignment evaluateAlignment(Map<String, List<GoldAnalysisEngine.Bar>> mtfBars) {
        if (mtfBars == null || mtfBars.isEmpty()) {
            return new MTFAlignment("محايد", "محايد", "محايد", "محايد", false, "بيانات الأطر الزمنية غير مكتملة");
        }

        String daily = evaluateTimeframeTrend(mtfBars.get("1day"));
        String fourHour = evaluateTimeframeTrend(mtfBars.get("4h"));
        String oneHour = evaluateTimeframeTrend(mtfBars.get("1h"));
        String fifteenMin = evaluateTimeframeTrend(mtfBars.get("15min"));

        boolean isBullishAligned = daily.contains("صاعد") && fourHour.contains("صاعد") && oneHour.contains("صاعد");
        boolean isBearishAligned = daily.contains("هابط") && fourHour.contains("هابط") && oneHour.contains("هابط");

        boolean fullyAligned = isBullishAligned || isBearishAligned;

        String summary = fullyAligned ? (isBullishAligned ? "توافق صاعد تام عبر جميع الأطر الزمنية 🟢" : "توافق هابط تام عبر جميع الأطر الزمنية 🔴")
                                      : "تعارض في الاتجاهات بين الأطر الزمنية ⚠️";

        return new MTFAlignment(daily, fourHour, oneHour, fifteenMin, fullyAligned, summary);
    }

    private static String evaluateTimeframeTrend(List<GoldAnalysisEngine.Bar> bars) {
        if (bars == null || bars.size() < 20) return "غير متوفر";
        int idx = bars.size() - 1;
        FeatureEngine.FeatureVector f = FeatureEngine.extractFeatures(bars, idx);

        if (bars.get(idx).c > f.ema50 && f.ema20 > f.ema50) return "صاعد (Bullish) 🟢";
        if (bars.get(idx).c < f.ema50 && f.ema20 < f.ema50) return "هابط (Bearish) 🔴";
        return "عرضي (Ranging) 🟡";
    }
}
