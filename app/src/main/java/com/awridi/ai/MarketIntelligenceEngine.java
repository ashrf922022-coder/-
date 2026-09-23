package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketIntelligenceEngine {

    public static class Bar {
        public double open;
        public double high;
        public double low;
        public double close;
        public double volume;

        public Bar(double open, double high, double low, double close, double volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    public static class Result {
        public double currentPrice;
        public double sma20;
        public double sma50;

        public double ema20;
        public double ema50;
        public double ema200;

        public double rsi14;
        public double macdHistogram;
        public double atr14;

        public double bollingerMiddle;
        public double bollingerUpper;
        public double bollingerLower;

        public double support;
        public double resistance;
        public double relativeVolume;

        public String trend = "غير محدد";
        public String trendStrength = "متوسط";
        public String momentum = "محايد";
        public String volatility = "متوسط";
        public String pattern = "غير محدد";

        public boolean breakout;
        public boolean pullback;

        public int marketScore;

        // Educational Signal & Confidence
        public String signal = "WAIT"; // BUY SETUP / SELL SETUP / WAIT / NO TRADE
        public double confidenceScore = 50.0; // 0 to 100%
        public List<String> signalReasons = new ArrayList<>();

        public double entryPrice;
        public double stopLoss;
        public double takeProfit1;
        public double takeProfit2;

        public String explanation = "";
    }

    public Result analyze(List<Bar> bars) {
        Result result = new Result();

        if (bars == null || bars.size() < 30) {
            result.explanation = "عدد البيانات غير كافٍ للتحليل. نحتاج إلى 30 شمعة على الأقل من Twelve Data.";
            return result;
        }

        int n = bars.size();
        Bar latest = bars.get(n - 1);
        result.currentPrice = latest.close;

        result.sma20 = sma(bars, 20);
        result.sma50 = sma(bars, 50);

        result.ema20 = ema(bars, 20);
        result.ema50 = ema(bars, 50);
        result.ema200 = ema(bars, 200);

        result.rsi14 = rsi(bars, 14);
        result.macdHistogram = macdHistogram(bars);
        result.atr14 = atr(bars, 14);

        double[] bb = bollingerBands(bars, 20, 2.0);
        result.bollingerMiddle = bb[0];
        result.bollingerUpper = bb[1];
        result.bollingerLower = bb[2];

        result.support = findSupport(bars, 20);
        result.resistance = findResistance(bars, 20);

        result.relativeVolume = relativeVolume(bars, 20);

        result.trend = detectTrend(result);
        result.trendStrength = (result.rsi14 > 60 || result.rsi14 < 40) ? "قوي 🚀" : "متوسط 📊";

        if (result.macdHistogram > 0.3) {
            result.momentum = "صعودي تسارعي 🟢";
        } else if (result.macdHistogram < -0.3) {
            result.momentum = "هبوطي ضاغط 🔴";
        } else {
            result.momentum = "محايد 🟡";
        }

        double atrPct = (result.atr14 / result.currentPrice) * 100.0;
        if (atrPct > 0.25) {
            result.volatility = "مرتفع جدًا ⚡";
        } else if (atrPct > 0.12) {
            result.volatility = "متوسط 📊";
        } else {
            result.volatility = "منخفض 💤";
        }

        result.breakout = detectBreakout(bars, result.resistance, result.support);
        result.pullback = detectPullback(bars, result);

        result.pattern = detectPattern(bars, result);

        result.marketScore = calculateScore(result);

        // Educational Trading Signal Logic
        evaluateSignal(result);

        result.explanation = buildExplanation(result);

        return result;
    }

    private void evaluateSignal(Result r) {
        r.signalReasons.clear();

        boolean isBullish = r.currentPrice > r.ema50 && r.ema20 > r.ema50;
        boolean isBearish = r.currentPrice < r.ema50 && r.ema20 < r.ema50;

        double confidence = 50.0;

        if (isBullish && r.rsi14 >= 45 && r.rsi14 <= 70 && r.macdHistogram > 0) {
            r.signal = "BUY SETUP 🟢";
            confidence += 20;
            r.signalReasons.add("السعر أعلى المتوسط EMA50 مع ترتيب صعودي للمتوسطات EMA20 > EMA50.");
            r.signalReasons.add("زخم MACD موجب ومؤشر RSI متوازن عند " + String.format(Locale.US, "%.1f", r.rsi14));

            if (r.breakout) {
                confidence += 15;
                r.signalReasons.add("اختراق صعودي أعلى حواجز المقاومة القريبة.");
            }
            if (r.pullback) {
                confidence += 10;
                r.signalReasons.add("ارتداد تصحيحي إيجابي بالقرب من دعم المتوسط EMA20.");
            }

            r.entryPrice = r.currentPrice;
            r.stopLoss = r.currentPrice - (r.atr14 * 1.5);
            r.takeProfit1 = r.currentPrice + (r.atr14 * 1.5);
            r.takeProfit2 = r.currentPrice + (r.atr14 * 3.0);

        } else if (isBearish && r.rsi14 <= 55 && r.rsi14 >= 30 && r.macdHistogram < 0) {
            r.signal = "SELL SETUP 🔴";
            confidence += 20;
            r.signalReasons.add("السعر أسفل المتوسط EMA50 مع ترتيب هبوطي للمتوسطات EMA20 < EMA50.");
            r.signalReasons.add("زخم MACD سالب ومؤشر RSI متوازن عند " + String.format(Locale.US, "%.1f", r.rsi14));

            if (r.breakout) {
                confidence += 15;
                r.signalReasons.add("كسر هبوطي أسفل حواجز الدعم القريبة.");
            }
            if (r.pullback) {
                confidence += 10;
                r.signalReasons.add("ارتداد تصحيحي سلبي بالقرب من مقاومة المتوسط EMA20.");
            }

            r.entryPrice = r.currentPrice;
            r.stopLoss = r.currentPrice + (r.atr14 * 1.5);
            r.takeProfit1 = r.currentPrice - (r.atr14 * 1.5);
            r.takeProfit2 = r.currentPrice - (r.atr14 * 3.0);

        } else if (r.volatility.contains("مرتفع جدًا")) {
            r.signal = "NO TRADE 🚫";
            confidence = 30.0;
            r.signalReasons.add("ارتفاع حاد في التقلب والذيل السعري مما يشكل خطورة على رأس المال.");
            r.entryPrice = r.currentPrice;
            r.stopLoss = 0; r.takeProfit1 = 0; r.takeProfit2 = 0;
        } else {
            r.signal = "WAIT ⏳";
            confidence = 50.0;
            r.signalReasons.add("عدم اكتمال شروط الشراء أو البيع وتداخل المتوسطات المتحركة.");
            r.entryPrice = r.currentPrice;
            r.stopLoss = 0; r.takeProfit1 = 0; r.takeProfit2 = 0;
        }

        r.confidenceScore = Math.min(95.0, confidence);
    }

    private double sma(List<Bar> bars, int period) {
        if (bars.size() < period) {
            period = bars.size();
        }
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            sum += bars.get(i).close;
        }
        return sum / period;
    }

    private double ema(List<Bar> bars, int period) {
        if (bars == null || bars.isEmpty()) {
            return 0;
        }
        int actualPeriod = Math.min(period, bars.size());
        double multiplier = 2.0 / (actualPeriod + 1.0);
        double ema = bars.get(0).close;
        for (int i = 1; i < bars.size(); i++) {
            ema = ((bars.get(i).close - ema) * multiplier) + ema;
        }
        return ema;
    }

    private double rsi(List<Bar> bars, int period) {
        if (bars.size() <= period) {
            return 50;
        }
        double gain = 0;
        double loss = 0;
        int start = bars.size() - period;
        for (int i = start; i < bars.size(); i++) {
            double change = bars.get(i).close - bars.get(i - 1).close;
            if (change > 0) gain += change;
            else loss -= change;
        }
        double averageGain = gain / period;
        double averageLoss = loss / period;
        if (averageLoss == 0) return 100;
        double rs = averageGain / averageLoss;
        return 100 - (100 / (1 + rs));
    }

    private double atr(List<Bar> bars, int period) {
        if (bars.size() < 2) return 0;
        int start = Math.max(1, bars.size() - period);
        double sum = 0;
        int count = 0;
        for (int i = start; i < bars.size(); i++) {
            Bar current = bars.get(i);
            Bar previous = bars.get(i - 1);
            double trueRange = Math.max(
                    current.high - current.low,
                    Math.max(
                            Math.abs(current.high - previous.close),
                            Math.abs(current.low - previous.close)
                    )
            );
            sum += trueRange;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private double macdHistogram(List<Bar> bars) {
        double ema12 = ema(bars, 12);
        double ema26 = ema(bars, 26);
        double macd = ema12 - ema26;

        if (bars.size() >= 9) {
            double[] values = new double[9];
            for (int i = 0; i < 9; i++) {
                int end = bars.size() - 9 + i;
                List<Bar> temp = bars.subList(0, end + 1);
                values[i] = ema(temp, 12) - ema(temp, 26);
            }
            double multiplier = 2.0 / 10.0;
            double signal = values[0];
            for (int i = 1; i < values.length; i++) {
                signal = ((values[i] - signal) * multiplier) + signal;
            }
            return macd - signal;
        }
        return macd;
    }

    private double[] bollingerBands(List<Bar> bars, int period, double deviationMultiplier) {
        double middle = sma(bars, period);
        int actualPeriod = Math.min(period, bars.size());
        double variance = 0;
        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {
            double difference = bars.get(i).close - middle;
            variance += difference * difference;
        }
        variance /= actualPeriod;
        double standardDeviation = Math.sqrt(variance);
        double upper = middle + (standardDeviation * deviationMultiplier);
        double lower = middle - (standardDeviation * deviationMultiplier);
        return new double[]{ middle, upper, lower };
    }

    private double findSupport(List<Bar> bars, int period) {
        int actualPeriod = Math.min(period, bars.size());
        double support = Double.MAX_VALUE;
        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {
            support = Math.min(support, bars.get(i).low);
        }
        return support == Double.MAX_VALUE ? 0 : support;
    }

    private double findResistance(List<Bar> bars, int period) {
        int actualPeriod = Math.min(period, bars.size());
        double resistance = Double.MIN_VALUE;
        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {
            resistance = Math.max(resistance, bars.get(i).high);
        }
        return resistance;
    }

    private double relativeVolume(List<Bar> bars, int period) {
        if (bars.size() < 2) return 1;
        int actualPeriod = Math.min(period, bars.size() - 1);
        double average = 0;
        int start = bars.size() - actualPeriod - 1;
        for (int i = start; i < bars.size() - 1; i++) {
            average += bars.get(i).volume;
        }
        average /= actualPeriod;
        double currentVolume = bars.get(bars.size() - 1).volume;
        return average == 0 ? 1 : currentVolume / average;
    }

    private String detectTrend(Result r) {
        if (r.ema20 > r.ema50 && r.ema50 > r.ema200) return "صاعد قوي 🟢";
        if (r.ema20 > r.ema50) return "صاعد 🟢";
        if (r.ema20 < r.ema50 && r.ema50 < r.ema200) return "هابط قوي 🔴";
        if (r.ema20 < r.ema50) return "هابط 🔴";
        return "عرضي / غير محدد 🟡";
    }

    private boolean detectBreakout(List<Bar> bars, double resistance, double support) {
        if (bars.size() < 2) return false;
        Bar current = bars.get(bars.size() - 1);
        Bar previous = bars.get(bars.size() - 2);
        boolean upsideBreakout = current.close > resistance && previous.close <= resistance;
        boolean downsideBreakout = current.close < support && previous.close >= support;
        return upsideBreakout || downsideBreakout;
    }

    private boolean detectPullback(List<Bar> bars, Result r) {
        if (bars.size() < 3) return false;
        Bar current = bars.get(bars.size() - 1);
        boolean bullishTrend = r.ema20 > r.ema50;
        boolean bearishTrend = r.ema20 < r.ema50;
        boolean bullishPullback = bullishTrend && current.close < r.ema20 && current.close > r.ema50;
        boolean bearishPullback = bearishTrend && current.close > r.ema20 && current.close < r.ema50;
        return bullishPullback || bearishPullback;
    }

    private String detectPattern(List<Bar> bars, Result r) {
        if (r.breakout) return "اختراق نطاق (Breakout)";
        if (r.pullback) return "تصحيح داخل الاتجاه (Pullback)";
        if (r.trend.contains("قوي")) return "استمرار الاتجاه (Continuation)";
        if (Math.abs(r.ema20 - r.ema50) < Math.abs(r.atr14) * 0.5) return "نطاق عرضي (Range / Sideways)";
        if (r.rsi14 > 70 || r.rsi14 < 30) return "احتمال انعكاس (Reversal)";
        return "غير محدد";
    }

    private int calculateScore(Result r) {
        int score = 50;
        if (r.trend.contains("صاعد قوي")) score += 20;
        else if (r.trend.contains("صاعد")) score += 10;
        else if (r.trend.contains("هابط قوي")) score -= 20;
        else if (r.trend.contains("هابط")) score -= 10;

        if (r.rsi14 >= 50 && r.rsi14 <= 70) score += 5;
        if (r.rsi14 < 30) score += 5;
        if (r.rsi14 > 70) score -= 5;

        if (r.macdHistogram > 0) score += 5;
        else if (r.macdHistogram < 0) score -= 5;

        if (r.breakout) {
            if (r.ema20 > r.ema50) score += 10;
            else score -= 10;
        }

        if (r.pullback) score += 3;
        if (r.relativeVolume > 1.5) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    private String buildExplanation(Result r) {
        StringBuilder text = new StringBuilder();
        text.append("• الاتجاه العام: ").append(r.trend).append(" (قوة الاتجاه: ").append(r.trendStrength).append(")\n");
        text.append("• الزخم: ").append(r.momentum).append(" | التقلب: ").append(r.volatility).append("\n");
        text.append("• النمط الفني المكتشف: ").append(r.pattern).append("\n");
        text.append(String.format(Locale.US, "• الدعم S1: $%.2f | المقاومة R1: $%.2f\n", r.support, r.resistance));
        text.append(String.format(Locale.US, "• مؤشر RSI(14): %.1f | MACD Hist: %.2f | ATR: $%.2f\n", r.rsi14, r.macdHistogram, r.atr14));
        text.append("• الإشارة التعليمية: ").append(r.signal).append(" (نسبة الثقة: ").append(String.format(Locale.US, "%.0f%%", r.confidenceScore)).append(")\n\n");
        text.append("• أسباب الإشارة:\n");
        for (String reason : r.signalReasons) {
            text.append("  - ").append(reason).append("\n");
        }
        return text.toString();
    }
}
