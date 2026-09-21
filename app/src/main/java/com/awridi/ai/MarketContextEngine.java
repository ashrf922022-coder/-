package com.awridi.ai;

/**
 * Market Context Engine measuring historical setup effectiveness within the current market regime.
 */
public class MarketContextEngine {

    public static class ContextReport {
        public final boolean isFavorableContext;
        public final String contextArabicRationale;

        public ContextReport(boolean isFavorableContext, String contextArabicRationale) {
            this.isFavorableContext = isFavorableContext;
            this.contextArabicRationale = contextArabicRationale;
        }
    }

    public static ContextReport evaluateContext(MarketStateEngine.MarketState state, PatternIntelligenceEngine.PatternResult pattern) {
        if (state == null || pattern == null) {
            return new ContextReport(false, "سياق السوق غير مكتمل");
        }

        if (state.primaryRegime == MarketStateEngine.Regime.HIGH_VOLATILITY) {
            return new ContextReport(false, "السوق يشهد تقلبات حادة متطرفة؛ يُنصح بتبني خيار الانتظار (WAIT) لحماية رأس المال.");
        }

        if (state.primaryRegime == MarketStateEngine.Regime.TREND_UP &&
            (pattern.pattern == PatternIntelligenceEngine.PatternType.BULLISH_BREAKOUT ||
             pattern.pattern == PatternIntelligenceEngine.PatternType.SUPPORT_BOUNCE ||
             pattern.pattern == PatternIntelligenceEngine.PatternType.BULLISH_ENGULFIG)) {
            return new ContextReport(true, "السياق التاريخي يدعم استمرار الاتجاه الصاعد مع توافق نمط التداول الحالي.");
        }

        if (state.primaryRegime == MarketStateEngine.Regime.TREND_DOWN &&
            (pattern.pattern == PatternIntelligenceEngine.PatternType.BEARISH_BREAKOUT ||
             pattern.pattern == PatternIntelligenceEngine.PatternType.RESISTANCE_REJECTION ||
             pattern.pattern == PatternIntelligenceEngine.PatternType.BEARISH_ENGULFING)) {
            return new ContextReport(true, "السياق التاريخي يدعم استمرار الاتجاه الهابط مع توافق نمط التداول الحالي.");
        }

        if (state.primaryRegime == MarketStateEngine.Regime.RANGE) {
            return new ContextReport(false, "السوق يتداول في نطاق عرضي ضيق بدون اتجاه واضح؛ احتمالية الإشارات الكاذبة مرتفعة.");
        }

        return new ContextReport(false, "السياق العام يحتاج لتأكيد إضافي قبل تنفيذ الصفقة.");
    }
}
