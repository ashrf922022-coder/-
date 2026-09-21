package com.awridi.ai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Trade Journal Engine maintaining market memory and tracking closed-loop learning performance
 * by setup, pattern, and market regime.
 */
public class TradeJournalEngine {

    private static final String PREF_KEY_JOURNAL = "awridi_trade_journal_v2";

    public static class JournalEntry {
        public String id;
        public long timestamp;
        public String symbol = "XAU/USD";
        public String regime;
        public String pattern;
        public String signal;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit;
        public double exitPrice;
        public double pnl;
        public double mfe;
        public double mae;
        public String outcomeStatus; // WIN, LOSS, OPEN

        public JSONObject toJsonObject() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("timestamp", timestamp);
            obj.put("symbol", symbol);
            obj.put("regime", regime);
            obj.put("pattern", pattern);
            obj.put("signal", signal);
            obj.put("entryPrice", entryPrice);
            obj.put("stopLoss", stopLoss);
            obj.put("takeProfit", takeProfit);
            obj.put("exitPrice", exitPrice);
            obj.put("pnl", pnl);
            obj.put("mfe", mfe);
            obj.put("mae", mae);
            obj.put("outcomeStatus", outcomeStatus);
            return obj;
        }

        public static JournalEntry fromJsonObject(JSONObject obj) throws Exception {
            JournalEntry j = new JournalEntry();
            j.id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
            j.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            j.symbol = obj.optString("symbol", "XAU/USD");
            j.regime = obj.optString("regime", "UNCERTAIN");
            j.pattern = obj.optString("pattern", "NO_PATTERN");
            j.signal = obj.optString("signal", "WAIT");
            j.entryPrice = obj.optDouble("entryPrice", 0);
            j.stopLoss = obj.optDouble("stopLoss", 0);
            j.takeProfit = obj.optDouble("takeProfit", 0);
            j.exitPrice = obj.optDouble("exitPrice", 0);
            j.pnl = obj.optDouble("pnl", 0);
            j.mfe = obj.optDouble("mfe", 0);
            j.mae = obj.optDouble("mae", 0);
            j.outcomeStatus = obj.optString("outcomeStatus", "OPEN");
            return j;
        }
    }

    public static void saveJournalEntry(SharedPreferences prefs, JournalEntry entry) {
        if (prefs == null || entry == null) return;
        List<JournalEntry> list = loadJournalEntries(prefs);
        list.add(0, entry);

        JSONArray arr = new JSONArray();
        for (JournalEntry j : list) {
            try {
                arr.put(j.toJsonObject());
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(PREF_KEY_JOURNAL, arr.toString()).apply();
    }

    public static List<JournalEntry> loadJournalEntries(SharedPreferences prefs) {
        List<JournalEntry> list = new ArrayList<>();
        if (prefs == null) return list;

        String jsonStr = prefs.getString(PREF_KEY_JOURNAL, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                list.add(JournalEntry.fromJsonObject(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }
}
