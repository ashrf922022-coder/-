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

public class PortfolioManager {

    public static final String PREF_KEY_PORTFOLIO_TRADES = "portfolio_trades_json";
    public static final String PREF_KEY_CAPITAL_HISTORY = "capital_history_json";
    public static final String PREF_KEY_MAX_DAILY_LOSS = "max_daily_loss_pct";

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
        public double pnl;
        public double rrRatio;
        public String status; // OPEN, WIN, LOSS, CLOSED
        public String notes;
        public String entryReason;
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
        public boolean isDailyLossExceeded;
    }

    // --- Trades Storage ---
    public static List<PortfolioTrade> loadTrades(SharedPreferences prefs) {
        List<PortfolioTrade> list = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(PREF_KEY_PORTFOLIO_TRADES, null);
            if (jsonStr == null) {
                jsonStr = prefs.getString(MainActivity.PREF_KEY_PAPER_TRADES, "[]");
            }
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
                t.pnl = obj.optDouble("pnl", 0.0);
                t.rrRatio = obj.optDouble("rrRatio", 1.5);
                t.status = obj.optString("status", "OPEN");
                t.notes = obj.optString("notes", "");
                t.entryReason = obj.optString("entryReason", "تحليل فني لمساعد AWRIDI AI");
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveTrades(SharedPreferences prefs, List<PortfolioTrade> list) {
        try {
            JSONArray arr = new JSONArray();
            for (PortfolioTrade t : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("date", t.date);
                obj.put("symbol", t.symbol);
                obj.put("type", t.type);
                obj.put("entryPrice", t.entryPrice);
                obj.put("exitPrice", t.exitPrice);
                obj.put("stopLoss", t.stopLoss);
                obj.put("tp1", t.tp1);
                obj.put("tp2", t.tp2);
                obj.put("lotSize", t.lotSize);
                obj.put("riskAmount", t.riskAmount);
                obj.put("pnl", t.pnl);
                obj.put("rrRatio", t.rrRatio);
                obj.put("status", t.status);
                obj.put("notes", t.notes);
                obj.put("entryReason", t.entryReason);
                arr.put(obj);
            }
            prefs.edit()
                .putString(PREF_KEY_PORTFOLIO_TRADES, arr.toString())
                .putString(MainActivity.PREF_KEY_PAPER_TRADES, arr.toString())
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Capital History Storage ---
    public static List<CapitalRecord> loadCapitalHistory(SharedPreferences prefs) {
        List<CapitalRecord> list = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(PREF_KEY_CAPITAL_HISTORY, "[]");
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveCapitalHistory(SharedPreferences prefs, List<CapitalRecord> list) {
        try {
            JSONArray arr = new JSONArray();
            for (CapitalRecord r : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", r.id);
                obj.put("timestamp", r.timestamp);
                obj.put("type", r.type);
                obj.put("amount", r.amount);
                obj.put("notes", r.notes);
                obj.put("oldVal", r.oldVal);
                obj.put("newVal", r.newVal);
                arr.put(obj);
            }
            prefs.edit().putString(PREF_KEY_CAPITAL_HISTORY, arr.toString()).apply();
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

        List<PortfolioTrade> trades = loadTrades(prefs);
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        double grossWins = 0;
        double grossLosses = 0;
        double currentBalance = summary.baseCapital;
        double peakBalance = summary.baseCapital;
        double maxDrawdownAmount = 0;
        double marginUsed = 0;

        for (PortfolioTrade t : trades) {
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
                } else if (t.pnl < 0) {
                    summary.losingTrades++;
                    double absLoss = Math.abs(t.pnl);
                    grossLosses += absLoss;
                    if (absLoss > summary.largestLoss) summary.largestLoss = absLoss;

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

        summary.todayLossPct = summary.baseCapital > 0 ? (summary.todayLossPnl / summary.baseCapital) * 100.0 : 0;
        summary.isDailyLossExceeded = summary.todayLossPct >= maxDailyLossPct;

        return summary;
    }

    // --- Trade Operations ---
    public static void executeTradeFromSignal(SharedPreferences prefs, GoldAnalysisEngine.AnalysisResult res) {
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
        t.lotSize = res.suggestedLot > 0 ? res.suggestedLot : (riskDiff > 0 ? riskAmount / (riskDiff * 100.0) : 0.1);
        t.rrRatio = res.riskRewardRatio > 0 ? res.riskRewardRatio : 1.5;

        t.status = "OPEN";
        t.pnl = 0.0;
        t.notes = "صفقة منفذة بناءً على تحليل محرك AWRIDI AI";
        t.entryReason = res.arabicExplanation != null ? res.arabicExplanation : "إشارة تداول توافق الشروط التقنية للذهب";

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
        double currentCap = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
        prefs.edit()
            .putString(MainActivity.PREF_KEY_CAPITAL, String.format(Locale.US, "%.2f", baseCapital))
            .putString(MainActivity.PREF_KEY_RISK_PCT, String.format(Locale.US, "%.2f", riskPct))
            .putString(PREF_KEY_MAX_DAILY_LOSS, String.format(Locale.US, "%.2f", maxDailyLossPct))
            .apply();

        if (Math.abs(currentCap - baseCapital) > 0.01) {
            addCapitalRecord(prefs, "CAPITAL_SET", baseCapital, "تعديل رأس المال الأساسي", currentCap, baseCapital);
        } else {
            addCapitalRecord(prefs, "RISK_UPDATE", riskPct, "تحديث إعدادات المخاطرة والحد اليومي", riskPct, maxDailyLossPct);
        }
    }
}
