package com.awridi.ai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TradeHistory
 * Responsible for local persistence, querying, filtering, and audit trail logging of paper trades.
 */
public class TradeHistory {

    public static final String PREF_KEY_PAPER_POSITIONS = "paper_trade_positions_json";
    public static final String PREF_KEY_AUDIT_TRAIL = "paper_trade_audit_trail_json";

    public static class AuditEvent {
        public String timestamp = "";
        public String tradeId = "";
        public String action = ""; // e.g. TRADE_CREATED, TP_HIT, SL_HIT, RISK_BLOCK, etc.
        public String reason = "";
        public String result = "";

        public AuditEvent() {
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        }

        public AuditEvent(String tradeId, String action, String reason, String result) {
            this();
            this.tradeId = tradeId != null ? tradeId : "";
            this.action = action != null ? action : "";
            this.reason = reason != null ? reason : "";
            this.result = result != null ? result : "";
        }

        public JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            try {
                json.put("timestamp", timestamp);
                json.put("tradeId", tradeId);
                json.put("action", action);
                json.put("reason", reason);
                json.put("result", result);
            } catch (Exception ignored) {}
            return json;
        }

        public static AuditEvent fromJsonObject(JSONObject json) {
            AuditEvent ev = new AuditEvent();
            if (json == null) return ev;
            ev.timestamp = json.optString("timestamp", "");
            ev.tradeId = json.optString("tradeId", "");
            ev.action = json.optString("action", "");
            ev.reason = json.optString("reason", "");
            ev.result = json.optString("result", "");
            return ev;
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + action + " | Trade: " + tradeId + " | Result: " + result + " | Reason: " + reason;
        }
    }

    // --- POSITIONS STORAGE & FILTERS ---

    public static List<TradePosition> loadPositions(SharedPreferences prefs) {
        List<TradePosition> list = new ArrayList<>();
        if (prefs == null) return list;

        try {
            String jsonStr = prefs.getString(PREF_KEY_PAPER_POSITIONS, "[]");
            if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
                return list;
            }

            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    TradePosition pos = TradePosition.fromJsonObject(obj);
                    list.add(pos);
                }
            } catch (Throwable unmockedError) {
                return parsePositionsFallback(jsonStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static List<TradePosition> parsePositionsFallback(String jsonStr) {
        List<TradePosition> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(jsonStr);
        while (m.find()) {
            String block = m.group();
            TradePosition pos = new TradePosition();
            pos.tradeId = optStringRegex(block, "tradeId", pos.tradeId);
            pos.symbol = optStringRegex(block, "symbol", MainActivity.GOLD_SYMBOL);
            try { pos.direction = TradePosition.Direction.valueOf(optStringRegex(block, "direction", "BUY")); } catch (Exception ignored) {}
            try { pos.status = TradePosition.Status.valueOf(optStringRegex(block, "status", "PENDING")); } catch (Exception ignored) {}
            pos.entryPrice = optDoubleRegex(block, "entryPrice", 0.0);
            pos.stopLoss = optDoubleRegex(block, "stopLoss", 0.0);
            pos.takeProfit = optDoubleRegex(block, "takeProfit", 0.0);
            pos.positionSize = optDoubleRegex(block, "positionSize", 0.1);
            pos.riskAmount = optDoubleRegex(block, "riskAmount", 0.0);
            pos.riskRewardRatio = optDoubleRegex(block, "riskRewardRatio", 0.0);
            pos.realizedPnL = optDoubleRegex(block, "realizedPnL", 0.0);
            pos.unrealizedPnL = optDoubleRegex(block, "unrealizedPnL", 0.0);
            pos.rMultiple = optDoubleRegex(block, "rMultiple", 0.0);
            pos.reason = optStringRegex(block, "reason", "");
            pos.rejectionReason = optStringRegex(block, "rejectionReason", "");
            pos.sourceDecisionId = optStringRegex(block, "sourceDecisionId", "");
            pos.conflictType = optStringRegex(block, "conflictType", "NONE");
            pos.resolution = optStringRegex(block, "resolution", "NONE");
            list.add(pos);
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

    public static void savePositions(SharedPreferences prefs, List<TradePosition> positions) {
        if (prefs == null || positions == null) return;
        try {
            try {
                JSONArray arr = new JSONArray();
                for (TradePosition p : positions) {
                    arr.put(p.toJsonObject());
                }
                prefs.edit().putString(PREF_KEY_PAPER_POSITIONS, arr.toString()).apply();
            } catch (Throwable unmockedError) {
                // JVM Fallback serializer for unit tests
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < positions.size(); i++) {
                    TradePosition p = positions.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"tradeId\":\"").append(p.tradeId != null ? p.tradeId : "").append("\",")
                      .append("\"symbol\":\"").append(p.symbol != null ? p.symbol : "").append("\",")
                      .append("\"direction\":\"").append(p.direction.name()).append("\",")
                      .append("\"status\":\"").append(p.status.name()).append("\",")
                      .append("\"entryPrice\":").append(String.format(Locale.US, "%.2f", p.entryPrice)).append(",")
                      .append("\"stopLoss\":").append(String.format(Locale.US, "%.2f", p.stopLoss)).append(",")
                      .append("\"takeProfit\":").append(String.format(Locale.US, "%.2f", p.takeProfit)).append(",")
                      .append("\"positionSize\":").append(String.format(Locale.US, "%.2f", p.positionSize)).append(",")
                      .append("\"riskAmount\":").append(String.format(Locale.US, "%.2f", p.riskAmount)).append(",")
                      .append("\"riskRewardRatio\":").append(String.format(Locale.US, "%.2f", p.riskRewardRatio)).append(",")
                      .append("\"realizedPnL\":").append(String.format(Locale.US, "%.2f", p.realizedPnL)).append(",")
                      .append("\"unrealizedPnL\":").append(String.format(Locale.US, "%.2f", p.unrealizedPnL)).append(",")
                      .append("\"rMultiple\":").append(String.format(Locale.US, "%.2f", p.rMultiple)).append(",")
                      .append("\"reason\":\"").append(p.reason != null ? p.reason.replace("\"", "'") : "").append("\",")
                      .append("\"rejectionReason\":\"").append(p.rejectionReason != null ? p.rejectionReason.replace("\"", "'") : "").append("\",")
                      .append("\"sourceDecisionId\":\"").append(p.sourceDecisionId != null ? p.sourceDecisionId : "").append("\",")
                      .append("\"conflictType\":\"").append(p.conflictType != null ? p.conflictType : "NONE").append("\",")
                      .append("\"resolution\":\"").append(p.resolution != null ? p.resolution : "NONE").append("\"")
                      .append("}");
                }
                sb.append("]");
                prefs.edit().putString(PREF_KEY_PAPER_POSITIONS, sb.toString()).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveTrade(SharedPreferences prefs, TradePosition pos) {
        if (prefs == null || pos == null) return;
        List<TradePosition> list = loadPositions(prefs);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).tradeId.equals(pos.tradeId)) {
                list.set(i, pos);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(pos);
        }
        savePositions(prefs, list);
    }

    public static TradePosition getTradeById(SharedPreferences prefs, String tradeId) {
        if (tradeId == null) return null;
        List<TradePosition> list = loadPositions(prefs);
        for (TradePosition p : list) {
            if (p.tradeId.equals(tradeId)) return p;
        }
        return null;
    }

    public static List<TradePosition> getOpenTrades(SharedPreferences prefs) {
        List<TradePosition> list = loadPositions(prefs);
        List<TradePosition> openList = new ArrayList<>();
        for (TradePosition p : list) {
            if (p.status == TradePosition.Status.OPEN || p.status == TradePosition.Status.PENDING) {
                openList.add(p);
            }
        }
        return openList;
    }

    public static List<TradePosition> getClosedTrades(SharedPreferences prefs) {
        List<TradePosition> list = loadPositions(prefs);
        List<TradePosition> closedList = new ArrayList<>();
        for (TradePosition p : list) {
            if (p.status == TradePosition.Status.CLOSED ||
                p.status == TradePosition.Status.TP_HIT ||
                p.status == TradePosition.Status.SL_HIT ||
                p.status == TradePosition.Status.CANCELLED) {
                closedList.add(p);
            }
        }
        return closedList;
    }

    public static List<TradePosition> getWinningTrades(SharedPreferences prefs) {
        List<TradePosition> list = loadPositions(prefs);
        List<TradePosition> wins = new ArrayList<>();
        for (TradePosition p : list) {
            if (p.realizedPnL > 0 || p.status == TradePosition.Status.TP_HIT) {
                wins.add(p);
            }
        }
        return wins;
    }

    public static List<TradePosition> getLosingTrades(SharedPreferences prefs) {
        List<TradePosition> list = loadPositions(prefs);
        List<TradePosition> losses = new ArrayList<>();
        for (TradePosition p : list) {
            if (p.realizedPnL < 0 || p.status == TradePosition.Status.SL_HIT) {
                losses.add(p);
            }
        }
        return losses;
    }

    public static List<TradePosition> getRejectedTrades(SharedPreferences prefs) {
        List<TradePosition> list = loadPositions(prefs);
        List<TradePosition> rejected = new ArrayList<>();
        for (TradePosition p : list) {
            if (p.status == TradePosition.Status.REJECTED || p.status == TradePosition.Status.BLOCKED) {
                rejected.add(p);
            }
        }
        return rejected;
    }

    public static boolean deleteTradeById(SharedPreferences prefs, String tradeId) {
        if (prefs == null || tradeId == null) return false;
        List<TradePosition> list = loadPositions(prefs);
        boolean removed = list.removeIf(p -> p.tradeId.equals(tradeId));
        if (removed) {
            savePositions(prefs, list);
        }
        return removed;
    }

    public static void clearHistory(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().remove(PREF_KEY_PAPER_POSITIONS).apply();
    }

    // --- AUDIT TRAIL PERSISTENCE ---

    public static List<AuditEvent> loadAuditTrail(SharedPreferences prefs) {
        List<AuditEvent> list = new ArrayList<>();
        if (prefs == null) return list;

        try {
            String jsonStr = prefs.getString(PREF_KEY_AUDIT_TRAIL, "[]");
            if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
                return list;
            }

            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    list.add(AuditEvent.fromJsonObject(arr.getJSONObject(i)));
                }
            } catch (Throwable unmockedError) {
                return parseAuditFallback(jsonStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static List<AuditEvent> parseAuditFallback(String jsonStr) {
        List<AuditEvent> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(jsonStr);
        while (m.find()) {
            String block = m.group();
            AuditEvent ev = new AuditEvent();
            ev.timestamp = optStringRegex(block, "timestamp", ev.timestamp);
            ev.tradeId = optStringRegex(block, "tradeId", "");
            ev.action = optStringRegex(block, "action", "");
            ev.reason = optStringRegex(block, "reason", "");
            ev.result = optStringRegex(block, "result", "");
            list.add(ev);
        }
        return list;
    }

    public static void logAuditEvent(SharedPreferences prefs, AuditEvent event) {
        if (prefs == null || event == null) return;
        List<AuditEvent> trail = loadAuditTrail(prefs);
        trail.add(event);

        try {
            try {
                JSONArray arr = new JSONArray();
                for (AuditEvent ev : trail) {
                    arr.put(ev.toJsonObject());
                }
                prefs.edit().putString(PREF_KEY_AUDIT_TRAIL, arr.toString()).apply();
            } catch (Throwable unmockedError) {
                // JVM Fallback serializer for unit tests
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < trail.size(); i++) {
                    AuditEvent ev = trail.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{")
                      .append("\"timestamp\":\"").append(ev.timestamp != null ? ev.timestamp : "").append("\",")
                      .append("\"tradeId\":\"").append(ev.tradeId != null ? ev.tradeId : "").append("\",")
                      .append("\"action\":\"").append(ev.action != null ? ev.action : "").append("\",")
                      .append("\"reason\":\"").append(ev.reason != null ? ev.reason.replace("\"", "'") : "").append("\",")
                      .append("\"result\":\"").append(ev.result != null ? ev.result.replace("\"", "'") : "").append("\"")
                      .append("}");
                }
                sb.append("]");
                prefs.edit().putString(PREF_KEY_AUDIT_TRAIL, sb.toString()).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clearAuditTrail(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().remove(PREF_KEY_AUDIT_TRAIL).apply();
    }
}
