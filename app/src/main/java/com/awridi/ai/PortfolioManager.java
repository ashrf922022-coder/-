package com.awridi.ai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortfolioManager {

    public static final String PREF_KEY_PORTFOLIO_TRADES = "portfolio_trades_json";
    public static final String PREF_KEY_CAPITAL_HISTORY = "capital_history_json";
    public static final String PREF_KEY_MAX_DAILY_LOSS = "max_daily_loss_pct";
    public static final String PREF_KEY_MAX_DAILY_TRADES = "max_daily_trades_count";

    public static class PortfolioTrade {
        public String id;
        public String date;
        public String symbol;
        public String type; // BUY / SELL
        public double entryPrice;
        public double exitPrice;
        public double stopLoss;
        public double tp1;
        public double tp2;
        public double lotSize;
        public double riskAmount;
        public double expectedProfit;
        public double pnl;
        public double rrRatio;
        public String status; // OPEN, WIN, LOSS, CLOSED
        public String notes;
        public String entryReason;
        public String signalSource; // ذكاء السوق / مساعد الذهب
    }

    public static class CapitalRecord {
        public String id;
        public String timestamp;
        public String type; // DEPOSIT, WITHDRAW, RISK_UPDATE, CAPITAL_SET
        public double amount;
        public String notes;
        public double oldVal;
        public double newVal;
    }

    public static class PortfolioSummary {
        public double baseCapital;
        public double currentBalance;
        public double availableBalance;
        public double totalPnl;
        public double returnPct;
        public int totalTrades;
        public int winningTrades;
        public int losingTrades;
        public double winRate; // 0 to 100
        public double profitFactor;
        public double maxDrawdown; // 0 to 100
        public double avgWin;
        public double avgLoss;
        public double largestWin;
        public double largestLoss;
        public int openTradesCount;
        public double openTradesMarginUsed;
        public double todayLossPnl;
        public double todayLossPct;
        public int todayTradesCount;
        public int maxDailyTrades;
        public boolean isDailyLossExceeded;
        public boolean isDailyTradesExceeded;
        public int longestLosingStreak;
    }

    public static class RiskValidationResult {
        public boolean isAllowed;
        public String messageArabic;
        public double riskAmountUsd;
        public double expectedProfitUsd;
        public double lotSize;
        public double rrRatio;
    }

    // --- Trades Storage (Unified with TradeHistory / TradePosition) ---
    public static List<PortfolioTrade> loadTrades(SharedPreferences prefs) {
        List<PortfolioTrade> list = new ArrayList<>();
        if (prefs == null) return list;

        // 1. Unified Source of Truth: TradeHistory (TradePosition)
        List<TradePosition> positions = TradeHistory.loadPositions(prefs);
        for (TradePosition pos : positions) {
            PortfolioTrade t = new PortfolioTrade();
            t.id = pos.tradeId;
            t.date = pos.openTime;
            t.symbol = pos.symbol != null ? pos.symbol : MainActivity.GOLD_SYMBOL;
            t.type = pos.direction != null ? pos.direction.name() : "BUY";
            t.entryPrice = pos.entryPrice;
            t.exitPrice = pos.currentPrice;
            t.stopLoss = pos.stopLoss;
            t.tp1 = pos.takeProfit;
            t.tp2 = pos.takeProfit;
            t.lotSize = pos.positionSize;
            t.riskAmount = pos.riskAmount;
            t.expectedProfit = pos.riskAmount * pos.riskRewardRatio;
            t.pnl = (pos.status == TradePosition.Status.OPEN || pos.status == TradePosition.Status.PENDING) ? pos.unrealizedPnL : pos.realizedPnL;
            t.rrRatio = pos.riskRewardRatio;
            if (pos.status == TradePosition.Status.TP_HIT || (pos.status == TradePosition.Status.CLOSED && pos.realizedPnL > 0)) {
                t.status = "WIN";
            } else if (pos.status == TradePosition.Status.SL_HIT || (pos.status == TradePosition.Status.CLOSED && pos.realizedPnL < 0)) {
                t.status = "LOSS";
            } else {
                t.status = pos.status != null ? pos.status.name() : "OPEN";
            }
            t.notes = pos.reason != null ? pos.reason : "";
            t.entryReason = pos.reason != null ? pos.reason : "صفقة ورقية مسجلة";
            t.signalSource = pos.sourceDecisionId != null ? pos.sourceDecisionId : "PaperTradeEngine";
            list.add(t);
        }

        // 2. Legacy fallback migration if TradeHistory is empty
        if (list.isEmpty()) {
            try {
                String jsonStr = prefs.getString(PREF_KEY_PORTFOLIO_TRADES, null);
                if (jsonStr == null) {
                    jsonStr = prefs.getString(MainActivity.PREF_KEY_PAPER_TRADES, "[]");
                }
                if (jsonStr != null && !jsonStr.trim().isEmpty() && !jsonStr.trim().equals("[]")) {
                    List<PortfolioTrade> legacyList;
                    try {
                        legacyList = new ArrayList<>();
                        JSONArray arr = new JSONArray(jsonStr);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            PortfolioTrade t = new PortfolioTrade();
                            t.id = obj.optString("id", UUID.randomUUID().toString());
                            t.date = obj.optString("date", "");
                            t.symbol = obj.optString("symbol", MainActivity.GOLD_SYMBOL);
                            t.type = obj.optString("type", "BUY");
                            t.entryPrice = obj.optDouble("entryPrice", 0.0);
                            t.exitPrice = obj.optDouble("exitPrice", 0.0);
                            t.stopLoss = obj.optDouble("stopLoss", 0.0);
                            t.tp1 = obj.optDouble("tp1", 0.0);
                            t.tp2 = obj.optDouble("tp2", 0.0);
                            t.lotSize = obj.optDouble("lotSize", 0.1);
                            t.riskAmount = obj.optDouble("riskAmount", 100.0);
                            t.expectedProfit = obj.optDouble("expectedProfit", 150.0);
                            t.pnl = obj.optDouble("pnl", 0.0);
                            t.rrRatio = obj.optDouble("rrRatio", 1.5);
                            t.status = obj.optString("status", "OPEN");
                            t.notes = obj.optString("notes", "");
                            t.entryReason = obj.optString("entryReason", "تحليل فني لمساعد AWRIDI AI");
                            t.signalSource = obj.optString("signalSource", "مساعد AWRIDI AI");
                            legacyList.add(t);
                        }
                    } catch (Throwable unmockedError) {
                        legacyList = parseTradesFallback(jsonStr);
                    }

                    for (PortfolioTrade pt : legacyList) {
                        TradePosition pos = new TradePosition();
                        pos.tradeId = pt.id;
                        pos.symbol = pt.symbol;
                        pos.direction = "SELL".equalsIgnoreCase(pt.type) ? TradePosition.Direction.SELL : TradePosition.Direction.BUY;
                        try {
                            pos.status = TradePosition.Status.valueOf(pt.status);
                        } catch (Exception ignored) {
                            pos.status = TradePosition.Status.OPEN;
                        }
                        pos.entryPrice = pt.entryPrice;
                        pos.stopLoss = pt.stopLoss;
                        pos.takeProfit = pt.tp1 > 0 ? pt.tp1 : pt.tp2;
                        pos.positionSize = pt.lotSize;
                        pos.riskAmount = pt.riskAmount;
                        pos.realizedPnL = pt.pnl;
                        pos.openTime = pt.date;
                        pos.reason = pt.notes;
                        TradeHistory.saveTrade(prefs, pos);
                        list.add(pt);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    private static List<PortfolioTrade> parseTradesFallback(String jsonStr) {
        List<PortfolioTrade> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(jsonStr);
        while (m.find()) {
            String block = m.group();
            PortfolioTrade t = new PortfolioTrade();
            t.id = optStringRegex(block, "id", UUID.randomUUID().toString());
            t.date = optStringRegex(block, "date", "");
            t.symbol = optStringRegex(block, "symbol", MainActivity.GOLD_SYMBOL);
            t.type = optStringRegex(block, "type", "BUY");
            t.entryPrice = optDoubleRegex(block, "entryPrice", 0.0);
            t.exitPrice = optDoubleRegex(block, "exitPrice", 0.0);
            t.stopLoss = optDoubleRegex(block, "stopLoss", 0.0);
            t.tp1 = optDoubleRegex(block, "tp1", 0.0);
            t.tp2 = optDoubleRegex(block, "tp2", 0.0);
            t.lotSize = optDoubleRegex(block, "lotSize", 0.1);
            t.riskAmount = optDoubleRegex(block, "riskAmount", 100.0);
            t.expectedProfit = optDoubleRegex(block, "expectedProfit", 150.0);
            t.pnl = optDoubleRegex(block, "pnl", 0.0);
            t.rrRatio = optDoubleRegex(block, "rrRatio", 1.5);
            t.status = optStringRegex(block, "status", "OPEN");
            t.notes = optStringRegex(block, "notes", "");
            t.entryReason = optStringRegex(block, "entryReason", "تحليل فني لمساعد AWRIDI AI");
            t.signalSource = optStringRegex(block, "signalSource", "مساعد AWRIDI AI");
            list.add(t);
        }
        return list;
    }

    private static String optStringRegex(String text, String key, String defVal) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        if (m.find()) return m.group(1);
        return defVal;
    }

    private static double optDoubleRegex(String text, String key, double defVal) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        }
        return defVal;
    }

    public static void saveTrades(SharedPreferences prefs, List<PortfolioTrade> list) {
        if (prefs == null || list == null) return;
        for (PortfolioTrade pt : list) {
            TradePosition pos = TradeHistory.getTradeById(prefs, pt.id);
            if (pos == null) {
                pos = new TradePosition();
                pos.tradeId = pt.id;
            }
            pos.symbol = pt.symbol != null ? pt.symbol : MainActivity.GOLD_SYMBOL;
            pos.direction = "SELL".equalsIgnoreCase(pt.type) ? TradePosition.Direction.SELL : TradePosition.Direction.BUY;
            if ("WIN".equalsIgnoreCase(pt.status) || "TP_HIT".equalsIgnoreCase(pt.status)) {
                pos.status = TradePosition.Status.TP_HIT;
            } else if ("LOSS".equalsIgnoreCase(pt.status) || "SL_HIT".equalsIgnoreCase(pt.status)) {
                pos.status = TradePosition.Status.SL_HIT;
            } else {
                try {
                    pos.status = TradePosition.Status.valueOf(pt.status);
                } catch (Exception ignored) {
                    pos.status = TradePosition.Status.CLOSED;
                }
            }
            pos.entryPrice = pt.entryPrice;
            pos.stopLoss = pt.stopLoss;
            pos.takeProfit = pt.tp1 > 0 ? pt.tp1 : pt.tp2;
            pos.positionSize = pt.lotSize;
            pos.riskAmount = pt.riskAmount;
            pos.realizedPnL = pt.pnl;
            pos.openTime = pt.date != null && !pt.date.isEmpty() ? pt.date : pos.openTime;
            pos.reason = pt.notes;
            TradeHistory.saveTrade(prefs, pos);
        }
    }

    // --- Capital History Storage ---
    public static List<CapitalRecord> loadCapitalHistory(SharedPreferences prefs) {
        List<CapitalRecord> list = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(PREF_KEY_CAPITAL_HISTORY, "[]");
            if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
                return list;
            }
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    CapitalRecord r = new CapitalRecord();
                    r.id = obj.optString("id");
                    r.timestamp = obj.optString("timestamp");
                    r.type = obj.optString("type");
                    r.amount = obj.optDouble("amount");
                    r.notes = obj.optString("notes");
                    r.oldVal = obj.optDouble("oldVal");
                    r.newVal = obj.optDouble("newVal");
                    list.add(r);
                }
            } catch (Throwable unmockedError) {
                // fallback
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveCapitalHistory(SharedPreferences prefs, List<CapitalRecord> list) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                CapitalRecord r = list.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"id\":\"").append(r.id != null ? r.id : "").append("\",")
                  .append("\"timestamp\":\"").append(r.timestamp != null ? r.timestamp : "").append("\",")
                  .append("\"type\":\"").append(r.type != null ? r.type : "").append("\",")
                  .append("\"amount\":").append(String.format(Locale.US, "%.2f", r.amount)).append(",")
                  .append("\"notes\":\"").append(r.notes != null ? r.notes : "").append("\",")
                  .append("\"oldVal\":").append(String.format(Locale.US, "%.2f", r.oldVal)).append(",")
                  .append("\"newVal\":").append(String.format(Locale.US, "%.2f", r.newVal)).append("}");
            }
            sb.append("]");
            prefs.edit().putString(PREF_KEY_CAPITAL_HISTORY, sb.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addCapitalRecord(SharedPreferences prefs, String type, double amount, String notes, double oldVal, double newVal) {
        List<CapitalRecord> list = loadCapitalHistory(prefs);
        CapitalRecord r = new CapitalRecord();
        r.id = UUID.randomUUID().toString();
        r.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        r.type = type;
        r.amount = amount;
        r.notes = notes;
        r.oldVal = oldVal;
        r.newVal = newVal;
        list.add(r);
        saveCapitalHistory(prefs, list);
    }

    // --- Portfolio Calculations ---
    public static PortfolioSummary calculateSummary(SharedPreferences prefs) {
        PortfolioSummary summary = new PortfolioSummary();
        summary.baseCapital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        double maxDailyLossPct = Double.parseDouble(prefs.getString(PREF_KEY_MAX_DAILY_LOSS, "3.0"));
        summary.maxDailyTrades = Integer.parseInt(prefs.getString(PREF_KEY_MAX_DAILY_TRADES, "5"));

        List<PortfolioTrade> trades = loadTrades(prefs);
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        double grossWins = 0;
        double grossLosses = 0;
        double currentBalance = summary.baseCapital;
        double peakBalance = summary.baseCapital;
        double maxDrawdownAmount = 0;
        double marginUsed = 0;

        int currentLossStreak = 0;
        int maxLossStreak = 0;

        for (PortfolioTrade t : trades) {
            if (t.date != null && t.date.startsWith(todayDateStr)) {
                summary.todayTradesCount++;
            }

            if ("OPEN".equals(t.status)) {
                summary.openTradesCount++;
                marginUsed += t.riskAmount; // Reserved risk margin
            } else {
                summary.totalTrades++;
                currentBalance += t.pnl;
                summary.totalPnl += t.pnl;

                if (t.pnl > 0) {
                    summary.winningTrades++;
                    grossWins += t.pnl;
                    if (t.pnl > summary.largestWin) summary.largestWin = t.pnl;
                    currentLossStreak = 0;
                } else if (t.pnl < 0) {
                    summary.losingTrades++;
                    double absLoss = Math.abs(t.pnl);
                    grossLosses += absLoss;
                    if (absLoss > summary.largestLoss) summary.largestLoss = absLoss;

                    currentLossStreak++;
                    if (currentLossStreak > maxLossStreak) {
                        maxLossStreak = currentLossStreak;
                    }

                    // Daily loss calculation for today
                    if (t.date != null && t.date.startsWith(todayDateStr)) {
                        summary.todayLossPnl += absLoss;
                    }
                }

                // Drawdown tracking
                if (currentBalance > peakBalance) {
                    peakBalance = currentBalance;
                } else {
                    double dd = peakBalance - currentBalance;
                    if (dd > maxDrawdownAmount) {
                        maxDrawdownAmount = dd;
                    }
                }
            }
        }

        summary.currentBalance = currentBalance;
        summary.openTradesMarginUsed = marginUsed;
        summary.availableBalance = Math.max(0, currentBalance - marginUsed);

        summary.returnPct = summary.baseCapital > 0 ? (summary.totalPnl / summary.baseCapital) * 100.0 : 0;
        summary.winRate = summary.totalTrades > 0 ? ((double) summary.winningTrades / summary.totalTrades) * 100.0 : 0;
        summary.profitFactor = grossLosses > 0 ? grossWins / grossLosses : (grossWins > 0 ? 99.0 : 0);
        summary.maxDrawdown = peakBalance > 0 ? (maxDrawdownAmount / peakBalance) * 100.0 : 0;
        summary.avgWin = summary.winningTrades > 0 ? grossWins / summary.winningTrades : 0;
        summary.avgLoss = summary.losingTrades > 0 ? grossLosses / summary.losingTrades : 0;
        summary.longestLosingStreak = maxLossStreak;

        summary.todayLossPct = summary.baseCapital > 0 ? (summary.todayLossPnl / summary.baseCapital) * 100.0 : 0;
        summary.isDailyLossExceeded = summary.todayLossPct >= maxDailyLossPct;
        summary.isDailyTradesExceeded = summary.todayTradesCount >= summary.maxDailyTrades;

        return summary;
    }

    // --- Risk Management Validation ---
    public static RiskValidationResult validateSignalRisk(SharedPreferences prefs, SignalEngine.SignalResult signalResult, String signalSource) {
        if (signalResult == null || !signalResult.valid || signalResult.signalType == SignalEngine.SignalType.HOLD) {
            RiskValidationResult res = new RiskValidationResult();
            res.isAllowed = false;
            res.messageArabic = signalResult == null ? "نتيجة محرك الإشارات غير متوفرة (null)." :
                    (!signalResult.valid ? "الإشارة غير صالحة: " + signalResult.rejectionReason :
                    "الإشارة الحالية هي HOLD (انتظار)، يمنع فتح صفقة تداول.");
            return res;
        }

        TradeSetupEngine setupEngine = new TradeSetupEngine();
        TradeSetup setup = setupEngine.createTradeSetupFromSignal(signalResult);

        return validateTradeRisk(prefs, setup.entryPrice, setup.stopLoss, setup.takeProfit, setup.direction.name(), signalSource);
    }

    public static RiskValidationResult validateTradeRisk(SharedPreferences prefs, double entryPrice, double stopLoss, double tp1, String signalType, String signalSource) {
        RiskValidationResult res = new RiskValidationResult();
        PortfolioSummary summary = calculateSummary(prefs);

        double capital = summary.baseCapital;
        double riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
        double maxDailyLossPct = Double.parseDouble(prefs.getString(PREF_KEY_MAX_DAILY_LOSS, "3.0"));

        TradeSetup.Direction dir = TradeSetup.Direction.BUY;
        if (signalType != null && signalType.contains("SELL")) {
            dir = TradeSetup.Direction.SELL;
        }

        if (signalType != null && (signalType.contains("WAIT") || signalType.contains("NO TRADE"))) {
            res.isAllowed = false;
            res.messageArabic = "تم رفض فتح الصفقة: الإشارة الحالية هي " + signalType + " وتحذر من التداول.";
            return res;
        }

        RiskManagementEngine riskEngine = new RiskManagementEngine();
        riskEngine.setMaxDailyLossPercentage(maxDailyLossPct);

        RiskManagementEngine.RiskResult riskRes = riskEngine.evaluateRisk(
                capital,
                riskPct,
                entryPrice,
                stopLoss,
                tp1,
                dir,
                summary.todayLossPnl
        );

        res.riskAmountUsd = riskRes.riskAmount;
        res.rrRatio = riskRes.riskRewardRatio;
        res.expectedProfitUsd = riskRes.riskAmount * riskRes.riskRewardRatio;
        res.lotSize = riskRes.positionSize;

        if (!riskRes.valid) {
            res.isAllowed = false;
            res.messageArabic = "تم رفض فتح الصفقة: " + riskRes.rejectionReason;
            return res;
        }

        if (summary.isDailyTradesExceeded) {
            res.isAllowed = false;
            res.messageArabic = String.format(Locale.US, "تم رفض فتح الصفقة: وصل عدد الصفقات اليومية (%d) إلى الحد الأقصى المسموح به (%d صفقات).", summary.todayTradesCount, summary.maxDailyTrades);
            return res;
        }

        if (summary.availableBalance < riskRes.riskAmount) {
            res.isAllowed = false;
            res.messageArabic = String.format(Locale.US, "تم رفض فتح الصفقة: الرصيد المتاح ($%.2f) غير كافٍ لتغطية هامش المخاطرة ($%.2f).", summary.availableBalance, riskRes.riskAmount);
            return res;
        }

        res.isAllowed = true;
        res.messageArabic = String.format(Locale.US, "تم قبول فتح الصفقة بنجاح من المصدر (%s): الشروط الفنية وإعدادات حماية رأس المال متوافقة تماماً.", signalSource != null ? signalSource : "محرك التحليل");
        return res;
    }

    // --- Trade Operations ---
    public static void executeTradeFromSignal(SharedPreferences prefs, GoldAnalysisEngine.AnalysisResult res) {
        executeTradeFromSignal(prefs, res, "مساعد AWRIDI AI");
    }

    public static void executeTradeFromSignal(SharedPreferences prefs, GoldAnalysisEngine.AnalysisResult res, String signalSource) {
        List<PortfolioTrade> list = loadTrades(prefs);
        PortfolioTrade t = new PortfolioTrade();
        t.id = UUID.randomUUID().toString();
        t.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        t.symbol = MainActivity.GOLD_SYMBOL;
        t.type = (res.signal != null && res.signal.contains("BUY")) ? "BUY" : "SELL";
        t.entryPrice = res.entryPrice;
        t.exitPrice = 0.0;
        t.stopLoss = res.stopLoss;
        t.tp1 = res.takeProfit1;
        t.tp2 = res.takeProfit2;

        double capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        double riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
        double riskAmount = capital * (riskPct / 100.0);
        t.riskAmount = riskAmount;

        double riskDiff = Math.abs(res.entryPrice - res.stopLoss);
        double rewardDiff = Math.abs(res.takeProfit1 - res.entryPrice);
        t.lotSize = res.suggestedLot > 0 ? res.suggestedLot : (riskDiff > 0 ? riskAmount / (riskDiff * 100.0) : 0.1);
        t.rrRatio = res.riskRewardRatio > 0 ? res.riskRewardRatio : (riskDiff > 0 ? rewardDiff / riskDiff : 1.5);
        t.expectedProfit = riskAmount * t.rrRatio;

        t.status = "OPEN";
        t.pnl = 0.0;
        t.notes = "صفقة تجريبية منفذة بناءً على تحليل محرك AWRIDI AI";
        t.entryReason = res.arabicExplanation != null ? res.arabicExplanation : "إشارة تداول توافق الشروط التقنية للذهب";
        t.signalSource = signalSource != null ? signalSource : "مساعد AWRIDI AI";

        list.add(t);
        saveTrades(prefs, list);
    }

    public static void closeTrade(SharedPreferences prefs, PortfolioTrade trade, boolean isWin, double customExitPrice) {
        List<PortfolioTrade> list = loadTrades(prefs);
        for (PortfolioTrade t : list) {
            if (t.id.equals(trade.id)) {
                t.status = isWin ? "WIN" : "LOSS";
                t.exitPrice = customExitPrice > 0 ? customExitPrice : (isWin ? t.tp1 : t.stopLoss);

                if (isWin) {
                    double rewardDiff = Math.abs(t.tp1 - t.entryPrice);
                    double riskDiff = Math.abs(t.entryPrice - t.stopLoss);
                    double ratio = riskDiff > 0 ? rewardDiff / riskDiff : 1.5;
                    t.pnl = t.riskAmount * ratio;
                    t.notes = "تم إغلاق الصفقة بنجاح على ربح (ضرب الهدف)";
                } else {
                    t.pnl = -t.riskAmount;
                    t.notes = "تم إغلاق الصفقة على خسارة (ضرب وقف الخسارة)";
                }
                break;
            }
        }
        saveTrades(prefs, list);
    }

    // --- Capital Management Operations ---
    public static void depositCapital(SharedPreferences prefs, double amount, String notes) {
        double currentCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        double newCap = currentCap + amount;
        prefs.edit().putString(MainActivity.PREF_KEY_CAPITAL, String.format(Locale.US, "%.2f", newCap)).apply();
        addCapitalRecord(prefs, "DEPOSIT", amount, notes, currentCap, newCap);
    }

    public static void withdrawCapital(SharedPreferences prefs, double amount, String notes) {
        double currentCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        double newCap = Math.max(0, currentCap - amount);
        prefs.edit().putString(MainActivity.PREF_KEY_CAPITAL, String.format(Locale.US, "%.2f", newCap)).apply();
        addCapitalRecord(prefs, "WITHDRAW", amount, notes, currentCap, newCap);
    }

    public static void updateCapitalSettings(SharedPreferences prefs, double baseCapital, double riskPct, double maxDailyLossPct) {
        updateCapitalSettings(prefs, baseCapital, riskPct, maxDailyLossPct, 5);
    }

    public static void updateCapitalSettings(SharedPreferences prefs, double baseCapital, double riskPct, double maxDailyLossPct, int maxDailyTrades) {
        double currentCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        prefs.edit()
            .putString(MainActivity.PREF_KEY_CAPITAL, String.format(Locale.US, "%.2f", baseCapital))
            .putString(MainActivity.PREF_KEY_RISK_PCT, String.format(Locale.US, "%.2f", riskPct))
            .putString(PREF_KEY_MAX_DAILY_LOSS, String.format(Locale.US, "%.2f", maxDailyLossPct))
            .putString(PREF_KEY_MAX_DAILY_TRADES, String.valueOf(maxDailyTrades))
            .apply();

        if (Math.abs(currentCap - baseCapital) > 0.01) {
            addCapitalRecord(prefs, "CAPITAL_SET", baseCapital, "تعديل رأس المال الأساسي", currentCap, baseCapital);
        } else {
            addCapitalRecord(prefs, "RISK_UPDATE", riskPct, "تحديث إعدادات المخاطرة والحدود اليومية", riskPct, maxDailyLossPct);
        }
    }
}
