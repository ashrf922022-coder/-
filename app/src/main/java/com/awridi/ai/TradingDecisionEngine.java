package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TradingDecisionEngine {

    public TradingDecisionResult evaluate(List<MarketIntelligenceEngine.Bar> bars) {
        return evaluate(bars, "XAU/USD", System.currentTimeMillis());
    }

    public TradingDecisionResult evaluate(List<MarketIntelligenceEngine.Bar> bars, String symbol, long timestamp) {
        TradingDecisionResult result = new TradingDecisionResult();
        result.symbol = symbol != null ? symbol : "XAU/USD";
        result.timestamp = timestamp;

        if (bars == null || bars.size() < 30) {
            result.decision = TradingDecisionResult.Decision.NO_TRADE;
            result.direction = TradingDecisionResult.Direction.UNKNOWN;
            result.signalQuality = "INVALID";
            result.confidence = 0.0;
            result.bullishScore = 0;
            result.bearishScore = 0;
            result.totalScore = 0;
            result.conflictingFactors.add("بيانات غير كافية للتحليل (تتطلب 30 شمعة على الأقل)");
            result.arabicExplanation = "عدم إمكانية إتخاذ قرار تداول لقلة البيانات المتاحة (بيانات غير كافية).";
            return result;
        }

        int n = bars.size();
        MarketIntelligenceEngine.Bar latest = bars.get(n - 1);
        result.currentPrice = latest.close;

        // 1. Calculate Indicators strictly up to latest candle (n-1)
        result.ema20 = calcEMA(bars, 20, n - 1);
        result.ema50 = calcEMA(bars, 50, n - 1);
        result.ema200 = calcEMA(bars, 200, n - 1);

        result.rsi = calcRSI(bars, 14, n - 1);
        result.macdHist = calcMACDHist(bars, n - 1);
        result.atrValue = calcATR(bars, 14, n - 1);

        double prevAtr = n >= 15 ? calcATR(bars, 14, n - 2) : result.atrValue;
        if (prevAtr > 0) {
            result.volatilityChange = ((result.atrValue - prevAtr) / prevAtr) * 100.0;
        }

        // Support and Resistance calculation (20 period window)
        result.support = findSupport(bars, 20, n - 1);
        result.resistance = findResistance(bars, 20, n - 1);

        // Price Structure: Higher High / Higher Low or Lower High / Lower Low or Breakout or Rejection
        result.priceStructure = analyzePriceStructure(bars, n - 1, result.support, result.resistance, result.atrValue);

        // Trend Analysis
        analyzeTrend(result);

        // Momentum Analysis
        analyzeMomentum(result);

        // Volatility Analysis
        analyzeVolatility(result);

        // Multi-Factor Alignment Evaluation
        evaluateMultiFactorDecision(result);

        // Generate Arabic Rationale
        generateArabicRationale(result);

        return result;
    }

    /**
     * Evaluates a historical bar slice up to `endIndex` to prevent look-ahead bias during backtesting.
     */
    public TradingDecisionResult evaluateAtCandle(List<MarketIntelligenceEngine.Bar> bars, int endIndex) {
        if (bars == null || endIndex < 0 || endIndex >= bars.size()) {
            TradingDecisionResult result = new TradingDecisionResult();
            result.decision = TradingDecisionResult.Decision.NO_TRADE;
            result.direction = TradingDecisionResult.Direction.UNKNOWN;
            result.signalQuality = "INVALID";
            result.confidence = 0.0;
            result.bullishScore = 0;
            result.bearishScore = 0;
            result.totalScore = 0;
            result.conflictingFactors.add("مؤشر الشمعة غير صالح أو خارج النطاق");
            result.arabicExplanation = "بيانات غير صالحة لاتخاذ قرار عند هذه الشمعة.";
            return result;
        }
        List<MarketIntelligenceEngine.Bar> slice = new ArrayList<>(bars.subList(0, endIndex + 1));
        return evaluate(slice);
    }

    private void analyzeTrend(TradingDecisionResult res) {
        if (res.currentPrice > res.ema200 && res.ema20 > res.ema50 && res.ema50 > res.ema200) {
            res.trend = "صاعد قوي";
            res.trendStrength = "قوية جداً";
        } else if (res.currentPrice > res.ema50 && res.ema20 > res.ema50) {
            res.trend = "صاعد";
            res.trendStrength = "قوية";
        } else if (res.currentPrice < res.ema200 && res.ema20 < res.ema50 && res.ema50 < res.ema200) {
            res.trend = "هابط قوي";
            res.trendStrength = "قوية جداً";
        } else if (res.currentPrice < res.ema50 && res.ema20 < res.ema50) {
            res.trend = "هابط";
            res.trendStrength = "قوية";
        } else {
            res.trend = "عرضي / غير محدد";
            res.trendStrength = "ضعيفة";
        }
    }

    private void analyzeMomentum(TradingDecisionResult res) {
        if (res.rsi >= 70) {
            res.momentum = "تشبع شرائي (Overbought)";
        } else if (res.rsi <= 30) {
            res.momentum = "تشبع بيعي (Oversold)";
        } else if (res.macdHist > 0 && res.rsi >= 50) {
            res.momentum = "صاعد قوي";
        } else if (res.macdHist < 0 && res.rsi < 50) {
            res.momentum = "هابط قوي";
        } else {
            res.momentum = "محايد / متوازن";
        }
    }

    private void analyzeVolatility(TradingDecisionResult res) {
        if (res.atrValue >= 4.5) {
            res.volatility = "مرتفع جدًا (حذر شديد)";
        } else if (res.atrValue >= 2.0) {
            res.volatility = "متوسط (طبيعي)";
        } else {
            res.volatility = "منخفض (نطاق ضيق)";
        }
    }

    private String analyzePriceStructure(List<MarketIntelligenceEngine.Bar> bars, int end, double support, double resistance, double atr) {
        if (end < 3) return "غير محدد";

        MarketIntelligenceEngine.Bar current = bars.get(end);
        MarketIntelligenceEngine.Bar prev = bars.get(end - 1);

        // Check Breakout
        if (current.close > resistance && prev.close <= resistance) {
            return "اختراق مقاومة صعودي (Breakout)";
        }
        if (current.close < support && prev.close >= support) {
            return "كسر دعم هبوطي (Breakout)";
        }

        // Check Rejection
        double upperWick = current.high - Math.max(current.open, current.close);
        double lowerWick = Math.min(current.open, current.close) - current.low;
        double candleBody = Math.abs(current.close - current.open);

        if (lowerWick > candleBody * 2 && current.low <= support + (atr * 0.3)) {
            return "رفض هبوطي عند الدعم (Bullish Rejection)";
        }
        if (upperWick > candleBody * 2 && current.high >= resistance - (atr * 0.3)) {
            return "رفض صعودي عند المقاومة (Bearish Rejection)";
        }

        // Check Higher Highs / Higher Lows vs Lower Highs / Lower Lows
        MarketIntelligenceEngine.Bar barMinus2 = bars.get(end - 2);
        if (current.high > prev.high && prev.high > barMinus2.high && current.low > prev.low && prev.low > barMinus2.low) {
            return "قمم وقيعان أعلى (Higher High / Higher Low)";
        }
        if (current.high < prev.high && prev.high < barMinus2.high && current.low < prev.low && prev.low < barMinus2.low) {
            return "قمم وقيعان أدنى (Lower High / Lower Low)";
        }

        return "حركة داخلية في النطاق";
    }

    private void evaluateMultiFactorDecision(TradingDecisionResult res) {
        SignalScoringEngine.evaluateScore(res);
    }

    private void generateArabicRationale(TradingDecisionResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("• القرار النهائي: ").append(res.decision.name()).append("\n");
        sb.append("• جودة الإشارة: ").append(res.signalQuality).append("\n");
        sb.append("• نسبة الثقة: ").append(String.format(Locale.US, "%.1f%%", res.confidence)).append("\n");
        sb.append("• النقاط الفنية: صاعدة (").append(res.bullishScore).append(") | هابطة (").append(res.bearishScore).append(") | الإجمالي (").append(res.totalScore).append(")\n");
        sb.append("• الاتجاه الفني: ").append(res.trend).append(" (قوة الاتجاه: ").append(res.trendStrength).append(")\n");
        sb.append("• حالة الزخم: ").append(res.momentum).append(" | RSI: ").append(String.format(Locale.US, "%.1f", res.rsi)).append("\n");
        sb.append("• مستوى التقلب ATR: ").append(String.format(Locale.US, "%.2f", res.atrValue)).append(" (").append(res.volatility).append(")\n");
        sb.append("• بنية السعر: ").append(res.priceStructure).append("\n");

        if (!res.supportingFactors.isEmpty()) {
            sb.append("\nالعوامل الداعمة للقرار:\n");
            for (String factor : res.supportingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        if (!res.conflictingFactors.isEmpty()) {
            sb.append("\nالعوامل المتعارضة / المحاذير:\n");
            for (String factor : res.conflictingFactors) {
                sb.append(" - ").append(factor).append("\n");
            }
        }

        res.arabicExplanation = sb.toString();
    }

    // Helper math methods calculating strictly up to `endIndex`
    public static double calcEMA(List<MarketIntelligenceEngine.Bar> bars, int period, int endIndex) {
        if (bars == null || bars.isEmpty() || endIndex < 0) return 0.0;
        int actualEnd = Math.min(endIndex, bars.size() - 1);
        List<MarketIntelligenceEngine.Bar> slice = bars.subList(0, actualEnd + 1);

        int actualPeriod = Math.min(period, slice.size());
        double multiplier = 2.0 / (actualPeriod + 1.0);
        double ema = slice.get(0).close;

        for (int i = 1; i < slice.size(); i++) {
            ema = ((slice.get(i).close - ema) * multiplier) + ema;
        }
        return ema;
    }

    public static double calcRSI(List<MarketIntelligenceEngine.Bar> bars, int period, int endIndex) {
        if (bars == null || endIndex < period) return 50.0;
        int actualEnd = Math.min(endIndex, bars.size() - 1);

        double gain = 0;
        double loss = 0;
        int start = actualEnd - period + 1;

        for (int i = start; i <= actualEnd; i++) {
            double change = bars.get(i).close - bars.get(i - 1).close;
            if (change > 0) gain += change;
            else loss -= change;
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public static double calcATR(List<MarketIntelligenceEngine.Bar> bars, int period, int endIndex) {
        if (bars == null || endIndex < 1) return 1.0;
        int actualEnd = Math.min(endIndex, bars.size() - 1);
        int start = Math.max(1, actualEnd - period + 1);

        double sum = 0;
        int count = 0;

        for (int i = start; i <= actualEnd; i++) {
            MarketIntelligenceEngine.Bar cur = bars.get(i);
            MarketIntelligenceEngine.Bar prev = bars.get(i - 1);

            double tr = Math.max(cur.high - cur.low,
                    Math.max(Math.abs(cur.high - prev.close), Math.abs(cur.low - prev.close)));
            sum += tr;
            count++;
        }
        return count == 0 ? 1.0 : sum / count;
    }

    public static double calcMACDHist(List<MarketIntelligenceEngine.Bar> bars, int endIndex) {
        if (bars == null || endIndex < 26) return 0.0;
        int actualEnd = Math.min(endIndex, bars.size() - 1);

        int startIdx = Math.max(26, actualEnd - 30);
        List<Double> macdSeries = new ArrayList<>();

        for (int i = startIdx; i <= actualEnd; i++) {
            double ema12 = calcEMA(bars, 12, i);
            double ema26 = calcEMA(bars, 26, i);
            macdSeries.add(ema12 - ema26);
        }

        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double multiplier = 2.0 / (9 + 1);
        double signalLine = macdSeries.get(0);

        for (int i = 1; i < macdSeries.size(); i++) {
            signalLine = ((macdSeries.get(i) - signalLine) * multiplier) + signalLine;
        }

        return macdLine - signalLine;
    }

    public static double findSupport(List<MarketIntelligenceEngine.Bar> bars, int period, int endIndex) {
        if (bars == null || endIndex < 1) return 0.0;
        int actualEnd = Math.min(endIndex - 1, bars.size() - 1);
        int start = Math.max(0, actualEnd - period + 1);

        double support = Double.MAX_VALUE;
        for (int i = start; i <= actualEnd; i++) {
            support = Math.min(support, bars.get(i).low);
        }
        return support == Double.MAX_VALUE ? 0.0 : support;
    }

    public static double findResistance(List<MarketIntelligenceEngine.Bar> bars, int period, int endIndex) {
        if (bars == null || endIndex < 1) return 0.0;
        int actualEnd = Math.min(endIndex - 1, bars.size() - 1);
        int start = Math.max(0, actualEnd - period + 1);

        double resistance = Double.MIN_VALUE;
        for (int i = start; i <= actualEnd; i++) {
            resistance = Math.max(resistance, bars.get(i).high);
        }
        return resistance == Double.MIN_VALUE ? 0.0 : resistance;
    }
}
