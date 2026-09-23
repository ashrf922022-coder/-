
package com.awridi.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MarketIntelligenceEngine {

    public static class Bar {
        public double open;
        public double high;
        public double low;
        public double close;
        public double volume;

        public Bar(double open, double high, double low,
                   double close, double volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    public static class Result {
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
        public String pattern = "غير محدد";

        public boolean breakout;
        public boolean pullback;

        public int marketScore;

        public String explanation = "";
    }

    public Result analyze(List<Bar> bars) {

        Result result = new Result();

        if (bars == null || bars.size() < 30) {
            result.explanation =
                    "عدد البيانات غير كافٍ للتحليل. نحتاج إلى 30 شمعة على الأقل.";
            return result;
        }

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

        result.breakout = detectBreakout(
                bars,
                result.resistance,
                result.support
        );

        result.pullback = detectPullback(bars, result);

        result.pattern = detectPattern(bars, result);

        result.marketScore = calculateScore(result);

        result.explanation = buildExplanation(result);

        return result;
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

            double change =
                    bars.get(i).close - bars.get(i - 1).close;

            if (change > 0) {
                gain += change;
            } else {
                loss -= change;
            }
        }

        double averageGain = gain / period;
        double averageLoss = loss / period;

        if (averageLoss == 0) {
            return 100;
        }

        double rs = averageGain / averageLoss;

        return 100 - (100 / (1 + rs));
    }

    private double atr(List<Bar> bars, int period) {

        if (bars.size() < 2) {
            return 0;
        }

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

        List<Bar> macdBars = new ArrayList<>();

        for (Bar bar : bars) {
            macdBars.add(
                    new Bar(
                            bar.open,
                            bar.high,
                            bar.low,
                            bar.close,
                            bar.volume
                    )
            );
        }

        double signal = macd;

        if (bars.size() >= 9) {
            double[] values = new double[9];

            for (int i = 0; i < 9; i++) {

                int end = bars.size() - 9 + i;

                List<Bar> temp = bars.subList(0, end + 1);

                values[i] =
                        ema(temp, 12) -
                        ema(temp, 26);
            }

            double multiplier = 2.0 / 10.0;

            signal = values[0];

            for (int i = 1; i < values.length; i++) {
                signal =
                        ((values[i] - signal) * multiplier) +
                        signal;
            }
        }

        return macd - signal;
    }

    private double[] bollingerBands(
            List<Bar> bars,
            int period,
            double deviationMultiplier) {

        double middle = sma(bars, period);

        int actualPeriod = Math.min(period, bars.size());

        double variance = 0;

        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {

            double difference =
                    bars.get(i).close - middle;

            variance += difference * difference;
        }

        variance /= actualPeriod;

        double standardDeviation = Math.sqrt(variance);

        double upper =
                middle + (standardDeviation * deviationMultiplier);

        double lower =
                middle - (standardDeviation * deviationMultiplier);

        return new double[]{
                middle,
                upper,
                lower
        };
    }

    private double findSupport(
            List<Bar> bars,
            int period) {

        int actualPeriod = Math.min(period, bars.size());

        double support = Double.MAX_VALUE;

        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {
            support = Math.min(support, bars.get(i).low);
        }

        return support == Double.MAX_VALUE ? 0 : support;
    }

    private double findResistance(
            List<Bar> bars,
            int period) {

        int actualPeriod = Math.min(period, bars.size());

        double resistance = Double.MIN_VALUE;

        for (int i = bars.size() - actualPeriod; i < bars.size(); i++) {
            resistance = Math.max(resistance, bars.get(i).high);
        }

        return resistance;
    }

    private double relativeVolume(
            List<Bar> bars,
            int period) {

        if (bars.size() < 2) {
            return 1;
        }

        int actualPeriod =
                Math.min(period, bars.size() - 1);

        double average = 0;

        int start =
                bars.size() - actualPeriod - 1;

        for (int i = start; i < bars.size() - 1; i++) {
            average += bars.get(i).volume;
        }

        average /= actualPeriod;

        double currentVolume =
                bars.get(bars.size() - 1).volume;

        if (average == 0) {
            return 1;
        }

        return currentVolume / average;
    }

    private String detectTrend(Result r) {

        if (r.ema20 > r.ema50 &&
                r.ema50 > r.ema200) {

            return "صاعد قوي";
        }

        if (r.ema20 > r.ema50) {
            return "صاعد";
        }

        if (r.ema20 < r.ema50 &&
                r.ema50 < r.ema200) {

            return "هابط قوي";
        }

        if (r.ema20 < r.ema50) {
            return "هابط";
        }

        return "عرضي";
    }

    private boolean detectBreakout(
            List<Bar> bars,
            double resistance,
            double support) {

        if (bars.size() < 2) {
            return false;
        }

        Bar current = bars.get(bars.size() - 1);
        Bar previous = bars.get(bars.size() - 2);

        boolean upsideBreakout =
                current.close > resistance &&
                previous.close <= resistance;

        boolean downsideBreakout =
                current.close < support &&
                previous.close >= support;

        return upsideBreakout || downsideBreakout;
    }

    private boolean detectPullback(
            List<Bar> bars,
            Result r) {

        if (bars.size() < 3) {
            return false;
        }

        Bar current = bars.get(bars.size() - 1);

        boolean bullishTrend =
                r.ema20 > r.ema50;

        boolean bearishTrend =
                r.ema20 < r.ema50;

        boolean bullishPullback =
                bullishTrend &&
                current.close < r.ema20 &&
                current.close > r.ema50;

        boolean bearishPullback =
                bearishTrend &&
                current.close > r.ema20 &&
                current.close < r.ema50;

        return bullishPullback || bearishPullback;
    }

    private String detectPattern(
            List<Bar> bars,
            Result r) {

        if (r.breakout) {
            return "اختراق";
        }

        if (r.pullback) {
            return "تصحيح داخل الاتجاه";
        }

        if ("صاعد قوي".equals(r.trend) ||
                "هابط قوي".equals(r.trend)) {

            return "استمرار الاتجاه";
        }

        if (Math.abs(r.ema20 - r.ema50) <
                Math.abs(r.atr14) * 0.5) {

            return "نطاق عرضي";
        }

        if (r.rsi14 > 70 ||
                r.rsi14 < 30) {

            return "احتمال انعكاس";
        }

        return "غير محدد";
    }

    private int calculateScore(Result r) {

        int score = 50;

        if ("صاعد قوي".equals(r.trend)) {
            score += 20;
        } else if ("صاعد".equals(r.trend)) {
            score += 10;
        } else if ("هابط قوي".equals(r.trend)) {
            score -= 20;
        } else if ("هابط".equals(r.trend)) {
            score -= 10;
        }

        if (r.rsi14 >= 50 && r.rsi14 <= 70) {
            score += 5;
        }

        if (r.rsi14 < 30) {
            score += 5;
        }

        if (r.rsi14 > 70) {
            score -= 5;
        }

        if (r.macdHistogram > 0) {
            score += 5;
        } else if (r.macdHistogram < 0) {
            score -= 5;
        }

        if (r.breakout) {
            if (r.ema20 > r.ema50) {
                score += 10;
            } else {
                score -= 10;
            }
        }

        if (r.pullback) {
            score += 3;
        }

        if (r.relativeVolume > 1.5) {
            score += 5;
        }

        return Math.max(0, Math.min(100, score));
    }

    private String buildExplanation(Result r) {

        StringBuilder text = new StringBuilder();

        text.append("الاتجاه: ")
                .append(r.trend)
                .append("\n");

        text.append("النمط: ")
                .append(r.pattern)
                .append("\n");

        text.append(String.format(
                Locale.US,
                "RSI: %.2f\n",
                r.rsi14
        ));

        text.append(String.format(
                Locale.US,
                "MACD Histogram: %.5f\n",
                r.macdHistogram
        ));

        text.append(String.format(
                Locale.US,
                "ATR: %.5f\n",
                r.atr14
        ));

        text.append(String.format(
                Locale.US,
                "الدعم: %.5f\n",
                r.support
        ));

        text.append(String.format(
                Locale.US,
                "المقاومة: %.5f\n",
                r.resistance
        ));

        text.append(String.format(
                Locale.US,
                "حجم التداول النسبي: %.2fx\n",
                r.relativeVolume
        ));

        if (r.breakout) {
            text.append("يوجد اختراق حديث لمستوى رئيسي.\n");
        }

        if (r.pullback) {
            text.append("يوجد تصحيح محتمل داخل الاتجاه الحالي.\n");
        }

        text.append("درجة ذكاء السوق: ")
                .append(r.marketScore)
                .append("/100");

        return text.toString();
    }
    }
