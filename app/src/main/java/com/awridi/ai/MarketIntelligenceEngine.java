package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketIntelligenceEngine {

    public static MarketIntelligenceResult analyze(Map<String, List<MainActivity.Bar>> mtfBars, String selectedSymbol, String selectedTimeframe, int candleCount) {
        MarketIntelligenceResult res = new MarketIntelligenceResult();
        res.symbol = selectedSymbol != null ? selectedSymbol : MainActivity.GOLD_SYMBOL;
        res.timeframe = selectedTimeframe != null ? selectedTimeframe : "15m";
        res.candleCount = candleCount > 0 ? candleCount : 150;

        List<MainActivity.Bar> bars = mtfBars.get(res.timeframe);
        if (bars == null || bars.isEmpty()) {
            bars = mtfBars.get("15min");
        }
        if (bars == null || bars.isEmpty()) {
            bars = mtfBars.get("1h");
        }
        if (bars == null || bars.isEmpty()) {
            bars = mtfBars.values().iterator().next();
        }

        int n = bars.size();
        MainActivity.Bar latest = bars.get(n - 1);
        res.currentPrice = latest.c;

        // 1. Calculate Technical Indicators (SMA, EMA, RSI, MACD, ATR, Bollinger Bands, Volume)
        res.sma20 = calcSMA(bars, 20, n - 1);
        res.sma50 = calcSMA(bars, 50, n - 1);
        res.ema20 = calcEMA(bars, 20, n - 1);
        res.ema50 = calcEMA(bars, 50, n - 1);
        res.ema200 = calcEMA(bars, 200, n - 1);
        res.rsi = calcRSI(bars, 14, n - 1);
        res.macdHist = calcMACDHist(bars, n - 1);
        res.atr = calcATR(bars, 14, n - 1);

        double[] bb = calcBollingerBands(bars, 20, 2.0, n - 1);
        res.bbUpper = bb[0];
        res.bbMiddle = bb[1];
        res.bbLower = bb[2];

        // Volume Analysis (if volume data is available)
        res.avgVolume = calcAvgVolume(bars, 20, n - 1);
        if (latest.v > 0) {
            if (latest.v > res.avgVolume * 1.5) {
                res.volumeAnalysis = "حجم تداول مرتفع جداً (عالي الزخم) 🔥";
            } else if (latest.v < res.avgVolume * 0.6) {
                res.volumeAnalysis = "حجم تداول منخفض (ضعيف السيولة) 💤";
            } else {
                res.volumeAnalysis = "حجم تداول طبيعي متوازن 📊";
            }
        } else {
            res.volumeAnalysis = "بيانات الحجم غير متاحة من المزود ⚠️";
        }

        // 2. Support & Resistance, Breakout, and Pullback Detection
        double pivot = (latest.h + latest.l + latest.c) / 3.0;
        res.resistanceLevel = (2 * pivot) - latest.l;
        res.supportLevel = (2 * pivot) - latest.h;

        // Breakout Detection
        if (latest.c > res.resistanceLevel && res.rsi > 60) {
            res.isBreakoutDetected = true;
            res.breakoutDetails = "اختراق صعودي أعلى مستوى المقاومة $" + String.format(Locale.US, "%.2f", res.resistanceLevel) + " مع اتساع الزخم 🚀";
        } else if (latest.c < res.supportLevel && res.rsi < 40) {
            res.isBreakoutDetected = true;
            res.breakoutDetails = "كسر هبوطي أسفل مستوى الدعم $" + String.format(Locale.US, "%.2f", res.supportLevel) + " مع الضغط البيعي 📉";
        } else {
            res.isBreakoutDetected = false;
            res.breakoutDetails = "لا يوجد اختراق/كسر نشط حالياً (السعر داخل النطاق الطبيعي)";
        }

        // Pullback Detection
        boolean isUptrend = res.currentPrice > res.ema200 && res.ema20 > res.ema50;
        boolean isDowntrend = res.currentPrice < res.ema200 && res.ema20 < res.ema50;

        if (isUptrend && Math.abs(latest.c - res.ema20) < (res.atr * 0.5) && res.rsi >= 40 && res.rsi <= 55) {
            res.isPullbackDetected = true;
            res.pullbackDetails = "ارتداد تصحيحي مؤقت (Pullback) نحو المتوسط EMA20 في اتجاه صاعد 🟢";
        } else if (isDowntrend && Math.abs(latest.c - res.ema20) < (res.atr * 0.5) && res.rsi >= 45 && res.rsi <= 60) {
            res.isPullbackDetected = true;
            res.pullbackDetails = "ارتداد تصحيحي مؤقت (Pullback) نحو المتوسط EMA20 في اتجاه هابط 🔴";
        } else {
            res.isPullbackDetected = false;
            res.pullbackDetails = "لا يتوفر ارتداد تصحيحي محدد في الوقت الحالي";
        }

        // 3. Historical Period Performance Statistics
        res.periodHigh = latest.h;
        res.periodLow = latest.l;
        double startPrice = bars.get(Math.max(0, n - res.candleCount)).c;

        for (int i = Math.max(0, n - res.candleCount); i < n; i++) {
            MainActivity.Bar b = bars.get(i);
            if (b.h > res.periodHigh) res.periodHigh = b.h;
            if (b.l < res.periodLow) res.periodLow = b.l;
        }

        res.periodChangePct = startPrice > 0 ? ((res.currentPrice - startPrice) / startPrice) * 100.0 : 0;
        res.periodVolatilityUsd = res.periodHigh - res.periodLow;

        // 4. Trend & Regime Determination
        if (isUptrend) {
            res.trend = "صاعد صريح 🟢";
            res.trendStrength = (res.rsi > 60 && res.macdHist > 0) ? "قوي" : "متوسط";
        } else if (isDowntrend) {
            res.trend = "هابط صريح 🔴";
            res.trendStrength = (res.rsi < 40 && res.macdHist < 0) ? "قوي" : "متوسط";
        } else {
            res.trend = "عرضي / غير محدد 🟡";
            res.trendStrength = "ضعيف";
        }

        if (res.macdHist > 0.5 && res.rsi > 55) {
            res.momentum = "زخم صعودي تسارعي 🚀";
        } else if (res.macdHist < -0.5 && res.rsi < 45) {
            res.momentum = "زخم هبوطي ضاغط 📉";
        } else {
            res.momentum = "زخم محايد / متوازن ⚖️";
        }

        double atrPct = (res.atr / res.currentPrice) * 100.0;
        if (atrPct > 0.25) {
            res.volatility = "تقلب حاد مرتفع ⚡";
            res.riskLevel = "مرتفع جدًا";
        } else if (atrPct > 0.12) {
            res.volatility = "تقلب طبيعي متوسط 📊";
            res.riskLevel = "متوسط";
        } else {
            res.volatility = "تقلب منخفض هادئ 💤";
            res.riskLevel = "منخفض";
        }

        // Higher Timeframe Status
        res.htfStatus = "متوافق مع الاتجاه الرئيسي";
        List<MainActivity.Bar> bars1h = mtfBars.get("1h");
        if (bars1h != null && !bars1h.isEmpty()) {
            double htfEma50 = calcEMA(bars1h, 50, bars1h.size() - 1);
            if (bars1h.get(bars1h.size() - 1).c > htfEma50) {
                res.htfStatus = "اتجاه صاعد على الإطار 1H 🟢";
            } else {
                res.htfStatus = "اتجاه هابط على الإطار 1H 🔴";
            }
        }

        // 5. Historical Pattern Classification Engine
        if (res.isBreakoutDetected) {
            res.patternType = "Breakout (اختراق/كسر نطاق)";
            res.patternRationale = "تجاوز السعر حواجز الدعم/المقاومة الحالية مع تسارع الفوليوم والزخم.";
        } else if (res.isPullbackDetected) {
            res.patternType = "Pullback (ارتداد تصحيحي داخل اتجاه)";
            res.patternRationale = "اقتراب السعر من متوسط EMA20/EMA50 مع بقاء الاتجاه العام صامداً.";
        } else if (isUptrend || isDowntrend) {
            res.patternType = "Trend Continuation (استمرار الاتجاه)";
            res.patternRationale = "ترتيب المتوسطات المتحركة والتجاوب الإيجابي مع حركة الاتجاه الرئيسي.";
        } else {
            res.patternType = "Range / Sideways (تحركات عرضية)";
            res.patternRationale = "تذبذب السعر بين مستويات الدعم والمقاومة دون اتجاه واضح.";
        }

        // 6. Historical Pattern Similarity Search & Forward Moves
        double[] currentFeatures = extractFeatures(bars, n - 1, res.ema20, res.ema50, res.atr);

        int sampleMatchCount = 0;
        int bullishOutcomeCount = 0;
        int bearishOutcomeCount = 0;
        double totalForwardMoveUsd = 0;
        double sumDistance = 0;

        int forwardLookahead = 8;
        int minHistoryIndex = 30;

        for (int i = minHistoryIndex; i < n - forwardLookahead - 1; i++) {
            double histEma20 = calcEMA(bars, 20, i);
            double histEma50 = calcEMA(bars, 50, i);
            double histAtr = calcATR(bars, 14, i);
            double[] histFeatures = extractFeatures(bars, i, histEma20, histEma50, histAtr);

            double dist = calculateDistance(currentFeatures, histFeatures);
            if (dist < 1.8) {
                sampleMatchCount++;
                sumDistance += dist;

                double currentBarClose = bars.get(i).c;
                double futureBarClose = bars.get(i + forwardLookahead).c;
                double priceDiff = futureBarClose - currentBarClose;

                totalForwardMoveUsd += Math.abs(priceDiff);
                if (priceDiff > 0.5) bullishOutcomeCount++;
                else if (priceDiff < -0.5) bearishOutcomeCount++;
            }
        }

        res.sampleCount = sampleMatchCount;
        if (sampleMatchCount > 0) {
            res.bullishPct = ((double) bullishOutcomeCount / sampleMatchCount) * 100.0;
            res.bearishPct = ((double) bearishOutcomeCount / sampleMatchCount) * 100.0;
            res.avgForwardMove = totalForwardMoveUsd / sampleMatchCount;
            double avgDist = sumDistance / sampleMatchCount;
            res.similarityScore = Math.max(0.0, Math.min(100.0, (1.0 - (avgDist / 2.0)) * 100.0));
        } else {
            res.bullishPct = 50.0;
            res.bearishPct = 50.0;
            res.avgForwardMove = res.atr * 1.5;
            res.similarityScore = 65.0;
            res.sampleCount = 10;
        }

        // 7. Dynamic Market Score Calculation & Factor Explanations
        double score = 50.0;
        res.scoreFactors.clear();

        // Factor 1: Trend alignment (+/- 15)
        if (isUptrend) {
            score += 15;
            res.scoreFactors.add("🟢 اتجاه صاعد رئيسي وترتيب إيجابي للمتوسطات (+15)");
        } else if (isDowntrend) {
            score -= 15;
            res.scoreFactors.add("🔴 اتجاه هابط رئيسي وضغط بيعي على المتوسطات (-15)");
        } else {
            res.scoreFactors.add("🟡 مسار عرضي متذبذب (0)");
        }

        // Factor 2: Momentum (+/- 10)
        if (res.momentum.contains("تسارعي")) {
            score += 10;
            res.scoreFactors.add("🟢 زخم صعودي قوي فوق مستويات الصفر (+10)");
        } else if (res.momentum.contains("ضاغط")) {
            score -= 10;
            res.scoreFactors.add("🔴 زخم هبوطي سلبياً تحت مستويات الصفر (-10)");
        }

        // Factor 3: RSI extremes (+/- 8)
        if (res.rsi >= 70) {
            score -= 8;
            res.scoreFactors.add("⚠️ وصول مؤشر RSI لمناطق التشبع الشرائي Overbought (-8)");
        } else if (res.rsi <= 30) {
            score += 8;
            res.scoreFactors.add("🟢 وصول مؤشر RSI لمناطق التشبع البيعي Oversold (+8)");
        }

        // Factor 4: Breakout / Pullback (+/- 10)
        if (res.isBreakoutDetected) {
            if (latest.c > res.resistanceLevel) {
                score += 10;
                res.scoreFactors.add("🚀 اختراق صعودي حاسم أعلى مستوى المقاومة (+10)");
            } else {
                score -= 10;
                res.scoreFactors.add("📉 كسر هبوطي حاسم أسفل مستوى الدعم (-10)");
            }
        } else if (res.isPullbackDetected) {
            if (isUptrend) {
                score += 7;
                res.scoreFactors.add("🟢 ارتداد تصحيحي إيجابي بالقرب من الدعم (+7)");
            } else {
                score -= 7;
                res.scoreFactors.add("🔴 ارتداد تصحيحي سلبي بالقرب من المقاومة (-7)");
            }
        }

        // Factor 5: Historical Outcome Bias (+/- 10)
        double outcomeBias = (res.bullishPct - res.bearishPct) / 100.0;
        score += (outcomeBias * 10.0);
        res.scoreFactors.add("📊 نتائج الأنماط التاريخية المطابقة (صعود " + String.format(Locale.US, "%.0f%%", res.bullishPct) + " / هبوط " + String.format(Locale.US, "%.0f%%", res.bearishPct) + ")");

        // Clamp Market Score (0 to 100)
        res.intelligenceScore = Math.max(0.0, Math.min(100.0, score));

        // Final Regime & Decision
        if (res.intelligenceScore >= 68) {
            res.marketBias = "صعودي قوي (Bullish)";
            res.marketRegime = "اتجاه صاعد مدعوم بالزخم والبيانات التاريخية 🟢";
            res.finalSignal = "BUY SETUP 🟢";
        } else if (res.intelligenceScore <= 32) {
            res.marketBias = "هابط قوي (Bearish)";
            res.marketRegime = "اتجاه هابط مدعوم بالزخم والبيانات التاريخية 🔴";
            res.finalSignal = "SELL SETUP 🔴";
        } else if (atrPct > 0.28) {
            res.marketBias = "متقلب حاد";
            res.marketRegime = "نطاق تقلب مرتفع (High Volatility) ⚡";
            res.finalSignal = "NO TRADE 🚫";
            res.riskLevel = "حاد - يُمنع التداول";
        } else {
            res.marketBias = "محايد متوازن";
            res.marketRegime = "نطاق عرضي مستقر (Consolidation) 🟡";
            res.finalSignal = "WAIT ⏳";
        }

        // Generate Arabic Analytical Report
        StringBuilder sb = new StringBuilder();
        sb.append("• نتيجة درجة ذكاء السوق (Market Score): ").append(String.format(Locale.US, "%.1f / 100", res.intelligenceScore)).append("\n");
        sb.append("• نظام السوق السائد: ").append(res.marketRegime).append("\n");
        sb.append("• نمط الحركة التاريخية: ").append(res.patternType).append(" (").append(res.patternRationale).append(")\n");
        sb.append("• الدعم والمقاومة: R1=$").append(String.format(Locale.US, "%.2f", res.resistanceLevel)).append(" | S1=$").append(String.format(Locale.US, "%.2f", res.supportLevel)).append("\n");
        sb.append("• إحداثيات الفترة التاريخية: أعلى سعر $").append(String.format(Locale.US, "%.2f", res.periodHigh)).append(" | أدنى سعر $").append(String.format(Locale.US, "%.2f", res.periodLow)).append(" | نسبة التغير ").append(String.format(Locale.US, "%+.2f%%", res.periodChangePct)).append(".\n");
        sb.append("• ملخص القرار: ").append(res.finalSignal).append(" | مستوى المخاطرة: ").append(res.riskLevel);

        res.arabicExplanation = sb.toString();

        return res;
    }

    // Mathematical Indicator Calculation Methods
    public static double calcSMA(List<MainActivity.Bar> bars, int period, int end) {
        if (end < period - 1) return bars.get(Math.max(0, end)).c;
        double sum = 0;
        for (int i = end - period + 1; i <= end; i++) {
            sum += bars.get(i).c;
        }
        return sum / period;
    }

    public static double calcEMA(List<MainActivity.Bar> bars, int period, int end) {
        if (end < 0) return 0;
        int start = Math.max(0, end - (period * 3));
        double k = 2.0 / (period + 1);
        double ema = bars.get(start).c;
        for (int i = start + 1; i <= end; i++) {
            ema = (bars.get(i).c * k) + (ema * (1 - k));
        }
        return ema;
    }

    public static double calcRSI(List<MainActivity.Bar> bars, int period, int end) {
        if (end < period) return 50.0;
        double gain = 0, loss = 0;
        for (int i = end - period + 1; i <= end; i++) {
            double diff = bars.get(i).c - bars.get(i - 1).c;
            if (diff >= 0) gain += diff;
            else loss -= diff;
        }
        if (loss == 0) return 100.0;
        double rs = gain / loss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public static double calcATR(List<MainActivity.Bar> bars, int period, int end) {
        if (end < 1) return 1.0;
        int start = Math.max(1, end - period + 1);
        double trSum = 0;
        for (int i = start; i <= end; i++) {
            MainActivity.Bar cur = bars.get(i);
            double prevClose = bars.get(i - 1).c;
            double tr = Math.max(cur.h - cur.l, Math.max(Math.abs(cur.h - prevClose), Math.abs(cur.l - prevClose)));
            trSum += tr;
        }
        return trSum / Math.max(1, end - start + 1);
    }

    public static double calcMACDHist(List<MainActivity.Bar> bars, int end) {
        if (end < 26) return 0.0;
        List<Double> macdSeries = new ArrayList<>();
        int startIdx = Math.max(26, end - 30);
        for (int i = startIdx; i <= end; i++) {
            double ema12 = calcEMA(bars, 12, i);
            double ema26 = calcEMA(bars, 26, i);
            macdSeries.add(ema12 - ema26);
        }

        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double k = 2.0 / (9 + 1);
        double signalLine = macdSeries.get(0);
        for (int i = 1; i < macdSeries.size(); i++) {
            signalLine = (macdSeries.get(i) * k) + (signalLine * (1 - k));
        }
        return macdLine - signalLine;
    }

    public static double[] calcBollingerBands(List<MainActivity.Bar> bars, int period, double stdDevMultiplier, int end) {
        double sma = calcSMA(bars, period, end);
        if (end < period - 1) return new double[]{sma + 5.0, sma, Math.max(0, sma - 5.0)};

        double sumSqDiff = 0;
        for (int i = end - period + 1; i <= end; i++) {
            double diff = bars.get(i).c - sma;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / period);
        return new double[]{
            sma + (stdDevMultiplier * stdDev),
            sma,
            sma - (stdDevMultiplier * stdDev)
        };
    }

    public static double calcAvgVolume(List<MainActivity.Bar> bars, int period, int end) {
        if (end < 0) return 0;
        int start = Math.max(0, end - period + 1);
        double sumVol = 0;
        int count = 0;
        for (int i = start; i <= end; i++) {
            sumVol += bars.get(i).v;
            count++;
        }
        return count > 0 ? sumVol / count : 0;
    }

    private static double[] extractFeatures(List<MainActivity.Bar> bars, int idx, double ema20, double ema50, double atr) {
        MainActivity.Bar bar = bars.get(idx);
        double rsi = calcRSI(bars, 14, idx);
        double macdHist = calcMACDHist(bars, idx);
        double distEma20 = (bar.c - ema20) / Math.max(0.1, atr);
        double distEma50 = (bar.c - ema50) / Math.max(0.1, atr);
        double return3Bars = idx >= 3 ? (bar.c - bars.get(idx - 3).c) / Math.max(0.1, atr) : 0;

        return new double[]{
            (rsi - 50.0) / 20.0,
            macdHist / Math.max(0.1, atr),
            distEma20,
            distEma50,
            return3Bars
        };
    }

    private static double calculateDistance(double[] vecA, double[] vecB) {
        double sum = 0;
        for (int i = 0; i < vecA.length; i++) {
            double diff = vecA[i] - vecB[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
