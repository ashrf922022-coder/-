package com.awridi.ai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GoldAnalysisEngine {

    public static class Bar {
        public double o, h, l, c, v;
        public Bar(double o, double h, double l, double c, double v) {
            this.o = o; this.h = h; this.l = l; this.c = c; this.v = v;
        }
    }

    public static class AnalysisResult {
        public double currentPrice;
        public String signal; // BUY SETUP, SELL SETUP, WAIT, NO TRADE
        public double confidenceScore;
        public double entryPrice, stopLoss, takeProfit1, takeProfit2, riskRewardRatio, suggestedLot;
        public String trend;
        public String htfTrend;
        public double rsi;
        public String rsiStatus;
        public double macdHist;
        public double ema20, ema50, ema200;
        public double atr;
        public String volatilityStatus;
        public double support, resistance;
        public String arabicExplanation;
    }

    public static List<Bar> fetchTwelveData(String sym, String interval, String apiKey, int count) throws Exception {
        String urlStr = "https://api.twelvedata.com/time_series?symbol=" + URLEncoder.encode(sym, "UTF-8")
                + "&interval=" + interval + "&outputsize=" + count + "&apikey=" + URLEncoder.encode(apiKey, "UTF-8");

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);

        if (conn.getResponseCode() != 200) {
            throw new Exception("استجابة غير صالحة من السيرفر: " + conn.getResponseCode());
        }

        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();

        JSONObject json = new JSONObject(sb.toString());
        if (!json.has("values")) {
            throw new Exception(json.optString("message", "لا تتوفر بيانات للرمز المطلوب"));
        }

        JSONArray arr = json.getJSONArray("values");
        List<Bar> bars = new ArrayList<>();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject obj = arr.getJSONObject(i);
            bars.add(new Bar(
                    obj.getDouble("open"),
                    obj.getDouble("high"),
                    obj.getDouble("low"),
                    obj.getDouble("close"),
                    obj.optDouble("volume", 0)
            ));
        }
        return bars;
    }

    public static AnalysisResult analyzeGold(Map<String, List<Bar>> mtfBars, SharedPreferences prefs) {
        AnalysisResult res = new AnalysisResult();
        List<Bar> bars15m = mtfBars.get("15min");
        if (bars15m == null || bars15m.isEmpty()) bars15m = mtfBars.values().iterator().next();

        int n = bars15m.size();
        Bar latest = bars15m.get(n - 1);
        res.currentPrice = latest.c;

        // Indicators on 15m
        res.rsi = calcRSI(bars15m, 14, n - 1);
        res.macdHist = calcMACDHist(bars15m, n - 1);
        res.ema20 = calcEMA(bars15m, 20, n - 1);
        res.ema50 = calcEMA(bars15m, 50, n - 1);
        res.ema200 = calcEMA(bars15m, 200, n - 1);
        res.atr = calcATR(bars15m, 14, n - 1);

        // Higher Timeframe Trend
        res.htfTrend = "متوافق";
        List<Bar> bars1h = mtfBars.get("1h");
        if (bars1h != null && !bars1h.isEmpty()) {
            double htfEma50 = calcEMA(bars1h, 50, bars1h.size() - 1);
            Bar last1h = bars1h.get(bars1h.size() - 1);
            if (last1h.c > htfEma50) res.htfTrend = "صاعد (1h) 🟢";
            else res.htfTrend = "هابط (1h) 🔴";
        }

        // RSI Status
        if (res.rsi >= 70) res.rsiStatus = "تشبع شرائي Overbought";
        else if (res.rsi <= 30) res.rsiStatus = "تشبع بيعي Oversold";
        else res.rsiStatus = "متوازن Neutral";

        // Support and Resistance Pivot Points
        double pivot = (latest.h + latest.l + latest.c) / 3.0;
        res.resistance = (2 * pivot) - latest.l;
        res.support = (2 * pivot) - latest.h;

        // Trend
        if (res.currentPrice > res.ema200 && res.ema20 > res.ema50) {
            res.trend = "صاعد قوي 🟢";
        } else if (res.currentPrice < res.ema200 && res.ema20 < res.ema50) {
            res.trend = "هابط قوي 🔴";
        } else {
            res.trend = "عرضي / غير محدد 🟡";
        }

        // Volatility
        res.volatilityStatus = res.atr > 4.0 ? "مرتفع جدًا" : res.atr > 2.0 ? "متوسط" : "منخفض";

        // Signal Engine
        boolean buyCondition = res.currentPrice > res.ema50 && res.rsi >= 45 && res.rsi <= 68 && res.macdHist > 0 && res.ema20 > res.ema50;
        boolean sellCondition = res.currentPrice < res.ema50 && res.rsi <= 55 && res.rsi >= 32 && res.macdHist < 0 && res.ema20 < res.ema50;

        if (buyCondition && !sellCondition) {
            res.signal = "BUY SETUP 🟢";
            res.confidenceScore = 0.85;
            res.entryPrice = res.currentPrice;
            res.stopLoss = res.entryPrice - (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice + (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice + (res.atr * 3.0);
        } else if (sellCondition && !buyCondition) {
            res.signal = "SELL SETUP 🔴";
            res.confidenceScore = 0.85;
            res.entryPrice = res.currentPrice;
            res.stopLoss = res.entryPrice + (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice - (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice - (res.atr * 3.0);
        } else if (Math.abs(res.rsi - 50) < 5 || res.volatilityStatus.equals("مرتفع جدًا")) {
            res.signal = "NO TRADE 🚫";
            res.confidenceScore = 0.30;
            res.entryPrice = res.currentPrice;
            res.stopLoss = 0; res.takeProfit1 = 0; res.takeProfit2 = 0;
        } else {
            res.signal = "WAIT ⏳";
            res.confidenceScore = 0.50;
            res.entryPrice = res.currentPrice;
            res.stopLoss = 0; res.takeProfit1 = 0; res.takeProfit2 = 0;
        }

        // Risk & Lot size
        if (res.stopLoss > 0) {
            double riskDiff = Math.abs(res.entryPrice - res.stopLoss);
            res.riskRewardRatio = Math.abs(res.takeProfit1 - res.entryPrice) / Math.max(0.1, riskDiff);

            double capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
            double riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
            double maxRiskUsd = capital * (riskPct / 100.0);
            res.suggestedLot = maxRiskUsd / (riskDiff * 100.0);
        }

        res.arabicExplanation = generateArabicRationale(res);
        return res;
    }

    public static String generateArabicRationale(AnalysisResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("• الاتجاه الحالي (15m): ").append(res.trend).append("\n");
        sb.append("• اتجاه الإطار الأكبر (1h): ").append(res.htfTrend).append("\n");

        if (res.signal != null && res.signal.contains("BUY")) {
            sb.append("• السبب: السعر فوق EMA50 وزخم MACD إيجابي مع استقرار RSI عند ").append(String.format(Locale.US, "%.1f", res.rsi)).append(".\n");
            sb.append("• المؤشرات المؤيدة: EMA20 أعلى من EMA50 ، شريط MACD موجب.\n");
            sb.append("• النصحية: دخول شراء مع الالتزام التام بوقف الخسارة عند $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append(".");
        } else if (res.signal != null && res.signal.contains("SELL")) {
            sb.append("• السبب: السعر أسفل EMA50 وزخم MACD سلبي مع استقرار RSI عند ").append(String.format(Locale.US, "%.1f", res.rsi)).append(".\n");
            sb.append("• المؤشرات المؤيدة: EMA20 أسفل EMA50 ، شريط MACD سالب.\n");
            sb.append("• النصحية: دخول بيع مع الالتزام بوقف الخسارة عند $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append(".");
        } else if (res.signal != null && res.signal.contains("NO TRADE")) {
            sb.append("• السبب: تقلب حاد في الأسواق أو تعادل القوى بين الشراء والبيع.\n");
            sb.append("• النصحية: يُمنع التداول حاليًا للحفاظ على رأس المال ومنع المخاطرة في ظروف غير مواتية.");
        } else {
            sb.append("• السبب: عدم اكتمال شروط الاستراتيجية (تداخل المتوسطات أو RSI متحيّد).\n");
            sb.append("• النصحية: يُفضل الانتظار حتى تتضح إشارة التداول القادمة بشكل أدق.");
        }
        return sb.toString();
    }

    public static double calcRSI(List<Bar> bars, int period, int end) {
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

    public static double calcEMA(List<Bar> bars, int period, int end) {
        if (end < 0) return 0;
        int start = Math.max(0, end - (period * 3));
        double k = 2.0 / (period + 1);
        double ema = bars.get(start).c;
        for (int i = start + 1; i <= end; i++) {
            ema = (bars.get(i).c * k) + (ema * (1 - k));
        }
        return ema;
    }

    public static double calcATR(List<Bar> bars, int period, int end) {
        if (end < 1) return 1.0;
        int start = Math.max(1, end - period + 1);
        double trSum = 0;
        for (int i = start; i <= end; i++) {
            Bar cur = bars.get(i);
            double prevClose = bars.get(i - 1).c;
            double tr = Math.max(cur.h - cur.l, Math.max(Math.abs(cur.h - prevClose), Math.abs(cur.l - prevClose)));
            trSum += tr;
        }
        return trSum / Math.max(1, end - start + 1);
    }

    public static double calcMACDHist(List<Bar> bars, int end) {
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
}
