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
        public String trendStrength = "غير محدد";
        public String momentum = "غير محدد";
        public String volatility = "غير محدد";
        public String marketState = "غير محدد";
        public String pattern = "غير محدد";

        public boolean breakout;
        public boolean pullback;

        public int marketScore;

        // Educational Signal fields
        public String educationalSignal = "WAIT ⏳";
        public double confidenceScore = 0.50; // 0.00 to 1.00
        public String signalReason = "";
        public List<String> supportingIndicators = new ArrayList<>();
        public List<String> conflictingIndicators = new ArrayList<>();

        // Detailed Indicator Evaluations
        public String rsiEvaluation = "";
        public String macdEvaluation = "";
        public String emaEvaluation = "";
        public String atrEvaluation = "";
        public String trendEvaluation = "";
        public String supportResistanceEvaluation = "";

        public String explanation = "";
    }

    public Result analyze(List<Bar> bars) {

        Result result = new Result();

        if (bars == null || bars.size() < 30) {
            result.explanation =
                    "عدد البيانات غير كافٍ للتحليل. نحتاج إلى 30 شمعة على الأقل.";
            return result;
        }

        Bar latestBar = bars.get(bars.size() - 1);
        result.currentPrice = latestBar.close;

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
        result.trendStrength = detectTrendStrength(result);
        result.momentum = detectMomentum(result);
        result.volatility = detectVolatility(result);

        result.breakout = detectBreakout(
                bars,
                result.resistance,
                result.support
        );

        result.pullback = detectPullback(bars, result);

        result.pattern = detectPattern(bars, result);
        result.marketState = detectMarketState(result);

        result.marketScore = calculateScore(result);

        // Compute indicator evaluations & educational signal
        evaluateIndicators(result);
        generateEducationalSignal(result);

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

        if (r.ema20 > r.ema50 && r.ema50 > r.ema200) {
            return "صاعد قوي";
        }

        if (r.ema20 > r.ema50) {
            return "صاعد";
        }

        if (r.ema20 < r.ema50 && r.ema50 < r.ema200) {
            return "هابط قوي";
        }

        if (r.ema20 < r.ema50) {
            return "هابط";
        }

        return "عرضي";
    }

    private String detectTrendStrength(Result r) {
        if ("صاعد قوي".equals(r.trend) || "هابط قوي".equals(r.trend)) {
            if (r.relativeVolume >= 1.2) {
                return "قوية جدًا (95%) 🔥";
            }
            return "قوية (80%) 💪";
        } else if ("صاعد".equals(r.trend) || "هابط".equals(r.trend)) {
            return "متوسطة (60%) ⚖️";
        }
        return "ضعيفة / عرضية (30%) 🟡";
    }

    private String detectMomentum(Result r) {
        if (r.rsi14 >= 70) {
            return "تشبع شرائي / تباطؤ زخم ⚠️";
        } else if (r.rsi14 <= 30) {
            return "تشبع بيعي / احتمالية ارتداد 🔄";
        } else if (r.macdHistogram > 0 && r.rsi14 >= 50) {
            return "زخم شرائي قوي 🚀";
        } else if (r.macdHistogram < 0 && r.rsi14 < 50) {
            return "زخم بيعي ضاغط 📉";
        }
        return "زخم متوازن / محايد ⚖️";
    }

    private String detectVolatility(Result r) {
        if (r.atr14 >= 4.0) {
            return "مرتفع جدًا (حذر من الانزلاق السعري) ⚡";
        } else if (r.atr14 >= 2.0) {
            return "متوسط (مثالي للتداول والتحليل) 👌";
        }
        return "منخفض (نطاق ضيق / تجميع) 💤";
    }

    private String detectMarketState(Result r) {
        if (r.breakout) {
            return "اختراق سعري نشط لمستويات رئيسية 🚀";
        }
        if (r.pullback) {
            return "تصحيح فني ملائم داخل اتجاه قائم 🎯";
        }
        if (r.rsi14 >= 70) {
            return "سوق في منطقة تشبع شرائي - تجنب الدخول المباشر ⚠️";
        }
        if (r.rsi14 <= 30) {
            return "سوق في منطقة تشبع بيعي - ترقب إشارات انعكاس 🔄";
        }
        if ("صاعد قوي".equals(r.trend)) {
            return "اتجاه صاعد منتظم مع هيمنة القوى الشرائية 🟢";
        }
        if ("هابط قوي".equals(r.trend)) {
            return "اتجاه هابط ضاغط مع هيمنة القوى البيعية 🔴";
        }
        return "نطاق تجميع عرضي وتوازن بين العرض والطلب 🟡";
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

    private void evaluateIndicators(Result r) {
        String rsiStatusStr = r.rsi14 >= 70 ? "تشبع شرائي (Overbought)" :
                r.rsi14 <= 30 ? "تشبع بيعي (Oversold)" :
                        r.rsi14 >= 50 ? "إيجابي / منطقة قوة شرائية" : "سلبي / منطقة ضغط بيعي";
        String rsiImpactStr = r.rsi14 >= 70 ? "يحذر من فتح صفقات شراء جديدة لاحتمال جني الأرباح." :
                r.rsi14 <= 30 ? "يشير إلى احتمال ارتداد صعودي قريب." :
                        r.rsi14 >= 50 ? "يدعم استمرار العزم الصعودي." : "يدعم استمرار العزم الهبوطي.";
        r.rsiEvaluation = String.format(Locale.US, "القراءة: %.2f (%s) | التأثير: %s", r.rsi14, rsiStatusStr, rsiImpactStr);

        String macdStatusStr = r.macdHistogram > 0 ? "أشرطة موجبة (زخم صعودي)" : "أشرطة سالبة (زخم هبوطي)";
        String macdImpactStr = r.macdHistogram > 0 ? "يعزز فرص إشارات الشراء لمطابقة الزخم." : "يعزز فرص إشارات البيع لمطابقة الزخم.";
        r.macdEvaluation = String.format(Locale.US, "القراءة: %.5f (%s) | التأثير: %s", r.macdHistogram, macdStatusStr, macdImpactStr);

        String emaStatusStr = (r.ema20 > r.ema50 && r.ema50 > r.ema200) ? "ترتيب صاعد مثالي (EMA20 > EMA50 > EMA200)" :
                (r.ema20 < r.ema50 && r.ema50 < r.ema200) ? "ترتيب هابط محكم (EMA20 < EMA50 < EMA200)" : "تداخل في المتوسطات المتحركة";
        String emaImpactStr = (r.ema20 > r.ema50) ? "السعر يتحرك أعلى المتوسطات مما يوفر دعمًا ديناميكيًا." : "السعر يتحرك أسفل المتوسطات مما يمثل مقاومة ديناميكية.";
        r.emaEvaluation = String.format(Locale.US, "EMA20=$%.2f | EMA50=$%.2f | EMA200=$%.2f\nالحالة: %s | التأثير: %s", r.ema20, r.ema50, r.ema200, emaStatusStr, emaImpactStr);

        String atrImpactStr = r.atr14 >= 4.0 ? "يتطلب توسيع وقف الخسارة وتقليل حجم اللوت لحماية رأس المال." :
                r.atr14 >= 2.0 ? "معدل تقلب طبيعي يسمح بوضع أهداف وقف خسارة وجني أرباح متوازنة." : "تقلب ضيق يشير إلى قرب انفجار سعري المرتقب.";
        r.atrEvaluation = String.format(Locale.US, "القراءة: $%.2f (%s) | التأثير: %s", r.atr14, r.volatility, atrImpactStr);

        String trendImpactStr = r.trend.contains("صاعد") ? "تفضيل صفقات الشراء والامتناع عن معاكسة الاتجاه." :
                r.trend.contains("هابط") ? "تفضيل صفقات البيع والامتناع عن الشراء المعاكس." : "الانتظار لحين خروج السعر من الحركة العرضية.";
        r.trendEvaluation = String.format(Locale.US, "الاتجاه: %s | قوة الاتجاه: %s | التأثير: %s", r.trend, r.trendStrength, trendImpactStr);

        double distSupport = Math.abs(r.currentPrice - r.support);
        double distResist = Math.abs(r.resistance - r.currentPrice);
        String srImpactStr = distResist < distSupport ? "السعر قريب من مستوى المقاومة ($" + String.format(Locale.US, "%.2f", r.resistance) + ") — يجب الحذر عند الشراء." :
                "السعر قريب من مستوى الدعم ($" + String.format(Locale.US, "%.2f", r.support) + ") — توفير منطقة حماية جيدة لصفقات الشراء.";
        r.supportResistanceEvaluation = String.format(Locale.US, "الدعم: $%.2f | المقاومة: $%.2f | التأثير: %s", r.support, r.resistance, srImpactStr);
    }

    private void generateEducationalSignal(Result r) {
        r.supportingIndicators.clear();
        r.conflictingIndicators.clear();

        boolean buyConditions = r.currentPrice > r.ema50 && r.rsi14 >= 45 && r.rsi14 <= 68 && r.macdHistogram > 0 && r.ema20 > r.ema50;
        boolean sellConditions = r.currentPrice < r.ema50 && r.rsi14 <= 55 && r.rsi14 >= 32 && r.macdHistogram < 0 && r.ema20 < r.ema50;

        int alignedCount = 0;

        if (buyConditions && !sellConditions) {
            r.educationalSignal = "BUY SETUP 🟢";

            if (r.currentPrice > r.ema50) { r.supportingIndicators.add("السعر أعلى من EMA50"); alignedCount++; }
            if (r.ema20 > r.ema50) { r.supportingIndicators.add("ترتيب المتوسطات صاعد (EMA20 > EMA50)"); alignedCount++; }
            if (r.macdHistogram > 0) { r.supportingIndicators.add("زخم MACD موجَب وإيجابي"); alignedCount++; }
            if (r.rsi14 >= 45 && r.rsi14 <= 68) { r.supportingIndicators.add("مؤشر RSI متوازن (" + String.format(Locale.US, "%.1f", r.rsi14) + ")"); alignedCount++; }
            if (r.relativeVolume >= 1.0) { r.supportingIndicators.add("حجم تداول أعلى من المتوسط"); alignedCount++; }

            if (r.currentPrice >= r.resistance - (r.atr14 * 0.5)) {
                r.conflictingIndicators.add("السعر قريب جداً من مستوى المقاومة ($" + String.format(Locale.US, "%.2f", r.resistance) + ")");
            }
            if (r.atr14 >= 4.0) {
                r.conflictingIndicators.add("ارتفاع حاد في التقلب ATR");
            }

            r.confidenceScore = Math.min(0.95, 0.60 + (alignedCount * 0.07));
            r.signalReason = "توافق المؤشرات الفنية للشرط الصعودي: السعر يتحرك أعلى المتوسط المتحرك 50 مع زخم إيجابي في MACD واستقرار قوة RSI بدون تشبع.";

        } else if (sellConditions && !buyConditions) {
            r.educationalSignal = "SELL SETUP 🔴";

            if (r.currentPrice < r.ema50) { r.supportingIndicators.add("السعر أسفل EMA50"); alignedCount++; }
            if (r.ema20 < r.ema50) { r.supportingIndicators.add("ترتيب المتوسطات هابط (EMA20 < EMA50)"); alignedCount++; }
            if (r.macdHistogram < 0) { r.supportingIndicators.add("زخم MACD سالِب وسلبي"); alignedCount++; }
            if (r.rsi14 <= 55 && r.rsi14 >= 32) { r.supportingIndicators.add("مؤشر RSI في النطاق الهابط (" + String.format(Locale.US, "%.1f", r.rsi14) + ")"); alignedCount++; }
            if (r.relativeVolume >= 1.0) { r.supportingIndicators.add("حجم تداول أعلى من المتوسط"); alignedCount++; }

            if (r.currentPrice <= r.support + (r.atr14 * 0.5)) {
                r.conflictingIndicators.add("السعر قريب جداً من مستوى الدعم ($" + String.format(Locale.US, "%.2f", r.support) + ")");
            }
            if (r.atr14 >= 4.0) {
                r.conflictingIndicators.add("ارتفاع حاد في التقلب ATR");
            }

            r.confidenceScore = Math.min(0.95, 0.60 + (alignedCount * 0.07));
            r.signalReason = "توافق المؤشرات الفنية للشرط الهبوطي: السعر كسَر المتوسط المتحرك 50 مع زخم سلبي متزايد في MACD وضغط بيعي في RSI.";

        } else if (r.rsi14 >= 70 || r.rsi14 <= 30 || r.atr14 >= 4.5) {
            r.educationalSignal = "NO TRADE 🚫";
            r.confidenceScore = 0.25;

            r.conflictingIndicators.add("مؤشر RSI في منطقة ذروة حرجة (" + String.format(Locale.US, "%.1f", r.rsi14) + ")");
            if (r.atr14 >= 4.5) r.conflictingIndicators.add("التقلب حاد وغير آمن للصفقات الجديدة");

            if (r.ema20 > r.ema50) r.supportingIndicators.add("الاتجاه العام لا يزال صاعداً لكن محفوف بالمخاطر");
            else r.supportingIndicators.add("الاتجاه العام لا يزال هابطاً لكن محفوف بالمخاطر");

            r.signalReason = "السوق يتواجد في حالة تشبع حرجة أو تقلب شديد للغاية، يمنع فتح صفقات جديدة للحفاظ على رأس المال.";

        } else {
            r.educationalSignal = "WAIT ⏳";
            r.confidenceScore = 0.50;

            if (r.ema20 > r.ema50) r.supportingIndicators.add("إشارة صعودية جزئية من المتوسطات");
            else r.supportingIndicators.add("إشارة هبوطية جزئية من المتوسطات");

            if (r.macdHistogram > 0) r.conflictingIndicators.add("MACD موجب لكن السعر غير متوافق تماماً");
            else r.conflictingIndicators.add("MACD سالب لكن السعر غير متوافق تماماً");

            r.signalReason = "عدم اكتمال شروط الشراء أو البيع الفنية، يُنصح بالانتظار حتى تتضح الحركة الاتجاهية القادمة.";
        }
    }

    private String buildExplanation(Result r) {

        StringBuilder text = new StringBuilder();

        text.append("الاتجاه: ")
                .append(r.trend)
                .append(" | قوة الاتجاه: ")
                .append(r.trendStrength)
                .append("\n");

        text.append("حالة السوق: ")
                .append(r.marketState)
                .append("\n");

        text.append("الإشارة التعليمية: ")
                .append(r.educationalSignal)
                .append(String.format(Locale.US, " (نسبة الثقة: %.0f%%)\n", r.confidenceScore * 100));

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
                "الدعم: $%.2f | المقاومة: $%.2f\n",
                r.support,
                r.resistance
        ));

        text.append("درجة ذكاء السوق: ")
                .append(r.marketScore)
                .append("/100");

        return text.toString();
    }
}
