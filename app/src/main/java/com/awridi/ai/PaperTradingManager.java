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

public class PaperTradingManager {
    public static final String PREF_KEY_PAPER_TRADES = "paper_trades_json";

    public static class PaperTrade {
        public String id, date, symbol, type, status, notes;
        public double entryPrice, stopLoss, tp1, tp2, pnl;
    }

    public static List<PaperTrade> loadPaperTrades(SharedPreferences prefs) {
        List<PaperTrade> list = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(PREF_KEY_PAPER_TRADES, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                PaperTrade t = new PaperTrade();
                t.id = obj.optString("id");
                t.date = obj.optString("date");
                t.symbol = obj.optString("symbol");
                t.type = obj.optString("type");
                t.entryPrice = obj.optDouble("entryPrice");
                t.stopLoss = obj.optDouble("stopLoss");
                t.tp1 = obj.optDouble("tp1");
                t.tp2 = obj.optDouble("tp2");
                t.status = obj.optString("status");
                t.pnl = obj.optDouble("pnl");
                t.notes = obj.optString("notes");
                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void savePaperTrades(SharedPreferences prefs, List<PaperTrade> list) {
        try {
            JSONArray arr = new JSONArray();
            for (PaperTrade t : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("date", t.date);
                obj.put("symbol", t.symbol);
                obj.put("type", t.type);
                obj.put("entryPrice", t.entryPrice);
                obj.put("stopLoss", t.stopLoss);
                obj.put("tp1", t.tp1);
                obj.put("tp2", t.tp2);
                obj.put("status", t.status);
                obj.put("pnl", t.pnl);
                obj.put("notes", t.notes);
                arr.put(obj);
            }
            prefs.edit().putString(PREF_KEY_PAPER_TRADES, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executeTradeFromSignal(SharedPreferences prefs, GoldAnalysisEngine.AnalysisResult res) {
        List<PaperTrade> list = loadPaperTrades(prefs);
        PaperTrade t = new PaperTrade();
        t.id = UUID.randomUUID().toString();
        t.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        t.symbol = MainActivity.GOLD_SYMBOL;
        t.type = res.signal != null && res.signal.contains("BUY") ? "BUY" : "SELL";
        t.entryPrice = res.entryPrice;
        t.stopLoss = res.stopLoss;
        t.tp1 = res.takeProfit1;
        t.tp2 = res.takeProfit2;
        t.status = "OPEN";
        t.pnl = 0;
        t.notes = "صفقة منفذة بناء على إشارة النظام";

        list.add(t);
        savePaperTrades(prefs, list);
    }

    public static void closeTrade(SharedPreferences prefs, PaperTrade trade, boolean isWin) {
        List<PaperTrade> list = loadPaperTrades(prefs);
        for (PaperTrade t : list) {
            if (t.id.equals(trade.id)) {
                t.status = isWin ? "WIN" : "LOSS";
                double capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
                double riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
                double riskAmount = capital * (riskPct / 100.0);
                t.pnl = isWin ? riskAmount * 1.5 : -riskAmount;
                t.notes = isWin ? "تم ضرب الهدف" : "تم ضرب وقف الخسارة";
                break;
            }
        }
        savePaperTrades(prefs, list);
    }
}
