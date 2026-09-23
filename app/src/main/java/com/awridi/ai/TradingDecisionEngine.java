package com.awridi.ai;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TradingDecisionEngine {

    public enum Decision {
        BUY,
        SELL,
        WAIT,
        NO_TRADE
    }

    public static class TradingDecisionResult {
        public String symbol = "XAU/USD";
        public Decision decisionEnum = Decision.NO_TRADE;
        public String decision = "NO TRADE";
        public double confidenceScore = 0.0;
        public String timestamp = "";

        // Technical Context
        public double currentPrice = 0.0;
        public String trend = "غير محدد";
        public String trendStrength = "غير محدد";
        public String momentum = "غير محدد";
        public String volatility = "غير محدد";
        public String priceStructure = "غير محدد";

        // Quantitative Indicators
        public double ema20 = 0.0;
        public double ema50 = 0.0;
        public double ema200 = 0.0;
        public double rsi = 50.0;
        public double macdHistogram = 0.0;
        public double atr = 0.0;
        public double support = 0.0;
        public double resistance = 0.0;
        public double relativeVolume = 1.0;

        // Interpretability Lists & Rationale
        public List<String> supportingFactors = new ArrayList<>();
        public List<String> conflictingFactors = new ArrayList<>();
        public String explanation = "";
    }

    public TradingDecisionResult evaluateDecisionFromGoldBars(List<GoldAnalysisEngine.Bar> goldBars, String symbol) {
        List<MarketIntelligenceEngine.Bar> miBars = new ArrayList<>();
        if (goldBars != null) {
            for (GoldAnalysisEngine.Bar b : goldBars) {
                if (b != null) {
                    miBars.add(new MarketIntelligenceEngine.Bar(b.o, b.h, b.l, b.c, b.v));
                }
            }
        }
        return evaluateDecision(miBars, symbol);
    }

    public TradingDecisionResult evaluateDecision(List<MarketIntelligenceEngine.Bar> bars, String symbol) {
        TradingDecisionResult result = new TradingDecisionResult();
        result.symbol = (symbol != null && !symbol.trim().isEmpty()) ? symbol : "XAU/USD";
        result.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        if (bars == null || bars.size() < 30) {
            result.decisionEnum = Decision.NO_TRADE;
            result.decision = "NO TRADE";
            result.confidenceScore = 0.0;
            result.explanation = "بيانات السوق غير كافية لإصدار قرار تداول آمن (نحتاج إلى 30 شمعة على الأقل).";
            result.conflictingFactors.add("عدد الشمعات غير كافٍ للتحليل الفني المتعدد");
            return result;
        }

        int n = bars.size();
        MarketIntelligenceEngine.Bar latest = bars.get(n - 1);
        result.currentPrice = latest.close;

        // 1. Quantitative Indicator Calculations (Strictly up to index n - 1, no future data)
        result.ema20 = calculateEMA(bars, 20, n - 1);
        result.ema50 = calculateEMA(bars, 50, n - 1);
        result.ema200 = calculateEMA(bars, 200, n - 1);
        result.rsi = calculateRSI(bars, 14, n - 1);
        result.macdHistogram = calculateMACDHistogram(bars, n - 1);
        result.atr = calculateATR(bars, 14, n - 1);

        double pivot = (latest.high + latest.low + latest.close) / 3.0;
        result.resistance = (2 * pivot) - latest.low;
        result.support = (2 * pivot) - latest.high;

        result.relativeVolume = calculateRelativeVolume(bars, 20, n - 1);

        // 2. Trend Factor Evaluation
        evaluateTrend(result);

        // 3. Momentum Factor Evaluation
        evaluateMomentum(result);

        // 4. Volatility Factor Evaluation
        evaluateVolatility(result);

        // 5. Price Structure Evaluation
        evaluatePriceStructure(bars, result, n - 1);

        // 6. Decision Matrix & Confluence Logic
        buildMultiFactorDecision(result);

        // 7. Generate Detailed Arabic Explanation
        buildExplanation(result);

        return result;
    }

    private void evaluateTrend(TradingDecisionResult r) {
        if (r.ema20 > r.ema50 && r.ema50 > r.ema200) {
            r.trend = "صاعد قوي 🟢";
            r.trendStrength = r.relativeVolume >= 1.2 ? "قوية جداً (95%) 🔥" : "قوية (80%) 💪";
        } else if (r.ema20 > r.ema50) {
            r.trend = "صاعد 🟢";
            r.trendStrength = "متوسطة (60%) ⚖️";
        } else if (r.ema20 < r.ema50 && r.ema50 < r.ema200) {
            r.trend = "هابط قوي 🔴";
            r.trendStrength = r.relativeVolume >= 1.2 ? "قوية جداً (95%) 🔥" : "قوية (80%) 💪";
        } else if (r.ema20 < r.ema50) {
            r.trend = "هابط 🔴";
            r.trendStrength = "متوسطة (60%) ⚖️";
        } else {
            r.trend = "عرضي / غير محدد 🟡";
            r.trendStrength = "ضعيفة (30%) 🟡";
        }
    }

    private void evaluateMomentum(TradingDecisionResult r) {
        if (r.rsi >= 80.0) {
            r.momentum = "تشبع شرائي / تباطؤ زخم ⚠️";
        } else if (r.rsi <= 20.0) {
            r.momentum = "تشبع بيعي / احتمالية ارتداد 🔄";
        } else if (r.macdHistogram > 0 && r.rsi >= 45.0) {
            r.momentum = "زخم شرائي إيجابي 🚀";
        } else if (r.macdHistogram < 0 && r.rsi <= 55.0) {
            r.momentum = "زخم بيعي ضاغط 📉";
        } else {
            r.momentum = "زخم محايد / متذبذب ⚖️";
        }
    }

    private void evaluateVolatility(TradingDecisionResult r) {
        if (r.atr >= 6.0) {
            r.volatility = "مرتفع جدًا (حذر من الانزلاق السعري) ⚡";
        } else if (r.atr >= 2.0) {
            r.volatility = "متوسط (مثالي للتداول) 👌";
        } else {
            r.volatility = "منخفض (نطاق ضيق / تجميع) 💤";
        }
    }

    private void evaluatePriceStructure(List<MarketIntelligenceEngine.Bar> bars, TradingDecisionResult r, int end) {
        if (end < 10) {
            r.priceStructure = "نطاق اعتيادي";
            return;
        }

        MarketIntelligenceEngine.Bar current = bars.get(end);
        MarketIntelligenceEngine.Bar previous = bars.get(end - 1);

        double wickUpper = current.high - Math.max(current.open, current.close);
        double wickLower = Math.min(current.open, current.close) - current.low;
        double bodySize = Math.abs(current.close - current.open);

        boolean upsideBreakout = current.close > r.resistance && previous.close <= r.resistance;
        boolean downsideBreakout = current.close < r.support && previous.close >= r.support;

        boolean bullishRejection = wickLower > (bodySize * 2.0) && current.low <= r.support;
        boolean bearishRejection = wickUpper > (bodySize * 2.0) && current.high >= r.resistance;

        int start = Math.max(0, end - 20);
        double recentHigh = Double.MIN_VALUE;
        double recentLow = Double.MAX_VALUE;

        for (int i = start; i < end; i++) {
            MarketIntelligenceEngine.Bar b = bars.get(i);
            if (b.high > recentHigh) recentHigh = b.high;
            if (b.low < recentLow) recentLow = b.low;
        }

        boolean isHigherHigh = current.high > recentHigh;
        boolean isLowerLow = current.low < recentLow;

        if (upsideBreakout) {
            r.priceStructure = "اختراق صعودي لمستوى المقاومة 🚀";
        } else if (downsideBreakout) {
            r.priceStructure = "كسر هبوطي لمستوى الدعم 📉";
        } else if (bullishRejection) {
            r.priceStructure = "رفض هبوطي وارتداد صعودي من الدعم 🔄";
        } else if (bearishRejection) {
            r.priceStructure = "رفض صعودي وصدمة بيعية عند المقاومة ⚠️";
        } else if (isHigherHigh) {
            r.priceStructure = "قمة أعلى جديدة (Higher High) 📈";
        } else if (isLowerLow) {
            r.priceStructure = "قاع أدنى جديد (Lower Low) 📉";
        } else {
            r.priceStructure = "تحرك داخل نطاق الهيكل السعري الحركي ⚖️";
        }
    }

    private void buildMultiFactorDecision(TradingDecisionResult r) {
        r.supportingFactors.clear();
        r.conflictingFactors.clear();

        int bullishScore = 0;
        int bearishScore = 0;

        // Bullish Factor Analysis
        if (r.currentPrice > r.ema50) {
            bullishScore++;
            r.supportingFactors.add("السعر يتحرك أعلى المتوسط المتحرك EMA 50");
        } else {
            bearishScore++;
            r.conflictingFactors.add("السعر أسفل المتوسط المتحرك EMA 50");
        }

        if (r.ema20 > r.ema50) {
            bullishScore++;
            r.supportingFactors.add("تقاطع صاعد للمتوسطات القريبة (EMA 20 > EMA 50)");
        } else if (r.ema20 < r.ema50) {
            bearishScore++;
            r.conflictingFactors.add("تقاطع هابط للمتوسطات القريبة (EMA 20 < EMA 50)");
        }

        if (r.macdHistogram > 0) {
            bullishScore++;
            r.supportingFactors.add("زخم MACD موجَب وإيجابي");
        } else if (r.macdHistogram < 0) {
            bearishScore++;
            r.conflictingFactors.add("زخم MACD سالِب وسلبي");
        }

        if (r.rsi >= 45.0 && r.rsi <= 80.0) {
            bullishScore++;
            r.supportingFactors.add(String.format(Locale.US, "مؤشر RSI في النطاق الصاعد المريح (%.1f)", r.rsi));
        } else if (r.rsi >= 20.0 && r.rsi <= 55.0) {
            bearishScore++;
            r.conflictingFactors.add(String.format(Locale.US, "مؤشر RSI في النطاق الهابط (%.1f)", r.rsi));
        } else if (r.rsi > 80.0) {
            r.conflictingFactors.add(String.format(Locale.US, "تحذير تشبع شرائي RSI (%.1f)", r.rsi));
        } else if (r.rsi < 20.0) {
            r.supportingFactors.add(String.format(Locale.US, "مؤشر RSI في منطقة تشبع بيعي (%.1f)", r.rsi));
        }

        if (r.priceStructure.contains("صعودي") || r.priceStructure.contains("Higher High") || r.priceStructure.contains("ارتداد")) {
            bullishScore++;
            r.supportingFactors.add("الهيكل السعري يوفر إشارة داعمة للصعود (" + r.priceStructure + ")");
        } else if (r.priceStructure.contains("هبوطي") || r.priceStructure.contains("Lower Low") || r.priceStructure.contains("صدمة")) {
            bearishScore++;
            r.conflictingFactors.add("الهيكل السعري يوفر إشارة ضاغطة للهبوط (" + r.priceStructure + ")");
        }

        if (r.relativeVolume >= 1.1) {
            r.supportingFactors.add("حجم تداول نسبي مرتفع يضمن السيولة (" + String.format(Locale.US, "%.2fx", r.relativeVolume) + ")");
        }

        // Final Decision Synthesis Matrix
        if (bullishScore >= 3 && bearishScore <= 1 && r.rsi < 85.0 && r.atr < 6.0) {
            r.decisionEnum = Decision.BUY;
            r.decision = "BUY";
            r.confidenceScore = Math.min(0.95, 0.60 + (bullishScore * 0.08));
        } else if (bearishScore >= 3 && bullishScore <= 1 && r.rsi > 15.0 && r.atr < 6.0) {
            r.decisionEnum = Decision.SELL;
            r.decision = "SELL";
            r.confidenceScore = Math.min(0.95, 0.60 + (bearishScore * 0.08));
        } else if (r.rsi >= 85.0 || r.rsi <= 15.0 || r.atr >= 6.0 || (bullishScore >= 2 && bearishScore >= 2)) {
            r.decisionEnum = Decision.WAIT;
            r.decision = "WAIT";
            r.confidenceScore = 0.45;
        } else {
            r.decisionEnum = Decision.WAIT;
            r.decision = "WAIT";
            r.confidenceScore = 0.50;
        }
    }

    private void buildExplanation(TradingDecisionResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("• القرار الفني المحسوب: ").append(r.decision)
                .append(String.format(Locale.US, " (نسبة الثقة: %.0f%%)\n", r.confidenceScore * 100));

        sb.append("• الاتجاه الفني: ").append(r.trend).append(" | قوة الاتجاه: ").append(r.trendStrength).append("\n");
        sb.append("• حالة الزخم: ").append(r.momentum).append("\n");
        sb.append("• هيكل السعر: ").append(r.priceStructure).append("\n");

        sb.append(String.format(Locale.US, "• المؤشرات الفنية الرقمية:\n  - السعر الحالي: $%.2f\n  - RSI (14): %.1f\n  - MACD Hist: %.5f\n  - EMA (20/50/200): $%.1f / $%.1f / $%.1f\n  - ATR (14): $%.2f (%s)\n  - S/R: الدعم $%.2f | المقاومة $%.2f\n",
                r.currentPrice, r.rsi, r.macdHistogram, r.ema20, r.ema50, r.ema200, r.atr, r.volatility, r.support, r.resistance));

        if (r.decisionEnum == Decision.BUY) {
            sb.append("• مبررات قرار الشراء (BUY): معظم المؤشرات الفنية تتطابق بشكل إيجابي مع تحرك السعر أعلى المتوسطات وزخم صعودي قوي مع هيكل سعري داعم.");
        } else if (r.decisionEnum == Decision.SELL) {
            sb.append("• مبررات قرار البيع (SELL): معظم المؤشرات الفنية تظهر ضغطاً بيعياً متزايداً أسفل المتوسطات مع زخم سلبي وهيكل سعري هابط.");
        } else if (r.decisionEnum == Decision.WAIT) {
            sb.append("• مبررات قرار الانتظار (WAIT): توجد إشارات متعارضة بين اتجاه المتوسطات وزخم RSI/MACD أو أن السوق يتواجد في حالة تشبع/تقلب مرتفع. يُفضل الانتظار لتأكيد الاتجاه.");
        } else {
            sb.append("• مبررات عدم التداول (NO TRADE): عدم توفر بيانات كافية أو تعذر التحليل الفني لرمز الأصل.");
        }

        r.explanation = sb.toString();
    }

    // Mathematical Indicator Helpers (Wilder's RSI Smoothing, 0 look-ahead bias)
    private static double calculateEMA(List<MarketIntelligenceEngine.Bar> bars, int period, int end) {
        if (end < 0) return 0.0;
        int start = Math.max(0, end - (period * 3));
        double k = 2.0 / (period + 1.0);
        double ema = bars.get(start).close;
        for (int i = start + 1; i <= end; i++) {
            ema = (bars.get(i).close * k) + (ema * (1.0 - k));
        }
        return ema;
    }

    private static double calculateRSI(List<MarketIntelligenceEngine.Bar> bars, int period, int end) {
        if (end < period) return 50.0;

        double gainSum = 0.0;
        double lossSum = 0.0;
        for (int i = 1; i <= period; i++) {
            double diff = bars.get(i).close - bars.get(i - 1).close;
            if (diff >= 0) gainSum += diff;
            else lossSum -= diff;
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        for (int i = period + 1; i <= end; i++) {
            double diff = bars.get(i).close - bars.get(i - 1).close;
            double gain = diff >= 0 ? diff : 0.0;
            double loss = diff < 0 ? -diff : 0.0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0.0) return avgGain == 0.0 ? 50.0 : 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private static double calculateATR(List<MarketIntelligenceEngine.Bar> bars, int period, int end) {
        if (end < 1) return 1.0;
        int start = Math.max(1, end - period + 1);
        double trSum = 0.0;
        for (int i = start; i <= end; i++) {
            MarketIntelligenceEngine.Bar cur = bars.get(i);
            double prevClose = bars.get(i - 1).close;
            double tr = Math.max(cur.high - cur.low, Math.max(Math.abs(cur.high - prevClose), Math.abs(cur.low - prevClose)));
            trSum += tr;
        }
        return trSum / Math.max(1, end - start + 1);
    }

    private static double calculateMACDHistogram(List<MarketIntelligenceEngine.Bar> bars, int end) {
        if (end < 26) return 0.0;
        List<Double> macdSeries = new ArrayList<>();
        int startIdx = Math.max(26, end - 30);
        for (int i = startIdx; i <= end; i++) {
            double ema12 = calculateEMA(bars, 12, i);
            double ema26 = calculateEMA(bars, 26, i);
            macdSeries.add(ema12 - ema26);
        }

        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double k = 2.0 / (9 + 1);
        double signalLine = macdSeries.get(0);
        for (int i = 1; i < macdSeries.size(); i++) {
            signalLine = (macdSeries.get(i) * k) + (signalLine * (1.0 - k));
        }
        return macdLine - signalLine;
    }

    private static double calculateRelativeVolume(List<MarketIntelligenceEngine.Bar> bars, int period, int end) {
        if (end < 2) return 1.0;
        int actualPeriod = Math.min(period, end);
        double sum = 0.0;
        for (int i = end - actualPeriod; i < end; i++) {
            sum += bars.get(i).volume;
        }
        double avg = sum / actualPeriod;
        if (avg == 0.0) return 1.0;
        return bars.get(end).volume / avg;
    }
}
