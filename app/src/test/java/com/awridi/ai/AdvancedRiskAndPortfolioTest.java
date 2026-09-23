package com.awridi.ai;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

public class AdvancedRiskAndPortfolioTest {

    @Test
    public void testRiskValidationDailyLossAndTradeLimits() {
        DummySharedPreferences prefs = new DummySharedPreferences();
        prefs.putString(MainActivity.PREF_KEY_CAPITAL, "10000");
        prefs.putString(MainActivity.PREF_KEY_RISK_PCT, "2.0"); // $200 risk
        prefs.putString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "5.0"); // $500 max daily loss
        prefs.putString(PortfolioManager.PREF_KEY_MAX_DAILY_TRADES, "3");

        // Initial check - should be allowed
        PortfolioManager.RiskValidationResult val1 = PortfolioManager.validateTradeRisk(
                prefs, 2650.0, 2640.0, 2665.0, "BUY SETUP", "ذكاء السوق"
        );
        assertTrue("Trade should be allowed initially", val1.isAllowed);
        assertEquals("Lot size should be calculated correctly", 0.20, val1.lotSize, 0.05);
        assertEquals("Risk USD should be 2% of 10000", 200.0, val1.riskAmountUsd, 0.01);
        assertEquals("Expected profit USD should match R:R 1.5", 300.0, val1.expectedProfitUsd, 0.01);

        // Execute 3 trades to reach max daily trades limit
        GoldAnalysisEngine.AnalysisResult dummySignal = new GoldAnalysisEngine.AnalysisResult();
        dummySignal.signal = "BUY SETUP";
        dummySignal.entryPrice = 2650.0;
        dummySignal.stopLoss = 2640.0;
        dummySignal.takeProfit1 = 2665.0;

        PortfolioManager.executeTradeFromSignal(prefs, dummySignal, "ذكاء السوق");
        PortfolioManager.executeTradeFromSignal(prefs, dummySignal, "ذكاء السوق");
        PortfolioManager.executeTradeFromSignal(prefs, dummySignal, "ذكاء السوق");

        PortfolioManager.PortfolioSummary summary = PortfolioManager.calculateSummary(prefs);
        System.out.println("DEBUG todayTradesCount: " + summary.todayTradesCount + " / maxDailyTrades: " + summary.maxDailyTrades);

        PortfolioManager.RiskValidationResult val2 = PortfolioManager.validateTradeRisk(
                prefs, 2650.0, 2640.0, 2665.0, "BUY SETUP", "ذكاء السوق"
        );
        System.out.println("DEBUG val2.isAllowed: " + val2.isAllowed + " | msg: " + val2.messageArabic);

        assertFalse("Trade should be rejected when max daily trades count is reached", val2.isAllowed);
        assertTrue("Rejection message should explain daily trade limit in Arabic", val2.messageArabic.contains("وصل عدد الصفقات اليومية"));
    }

    @Test
    public void testPortfolioRealStatisticsCalculations() {
        DummySharedPreferences prefs = new DummySharedPreferences();
        prefs.putString(MainActivity.PREF_KEY_CAPITAL, "10000");

        List<PortfolioManager.PortfolioTrade> list = new ArrayList<>();

        PortfolioManager.PortfolioTrade t1 = new PortfolioTradeBuilder("1", "BUY", 2650.0, 2640.0, 2665.0, 200.0, "WIN", "ذكاء السوق").build();
        PortfolioManager.PortfolioTrade t2 = new PortfolioTradeBuilder("2", "BUY", 2650.0, 2640.0, 2665.0, 200.0, "WIN", "ذكاء السوق").build();
        PortfolioManager.PortfolioTrade t3 = new PortfolioTradeBuilder("3", "SELL", 2650.0, 2660.0, 2635.0, -100.0, "LOSS", "مساعد AWRIDI AI").build();

        list.add(t1);
        list.add(t2);
        list.add(t3);

        PortfolioManager.saveTrades(prefs, list);

        PortfolioManager.PortfolioSummary summary = PortfolioManager.calculateSummary(prefs);

        assertEquals("Total trades count should be 3", 3, summary.totalTrades);
        assertEquals("Winning trades should be 2", 2, summary.winningTrades);
        assertEquals("Losing trades should be 1", 1, summary.losingTrades);
        assertTrue("Win rate should be ~66.7%", Math.abs(66.67 - summary.winRate) < 1.0);
        assertEquals("Total PnL should be $300", 300.0, summary.totalPnl, 0.01);
        assertEquals("Average win should be $200", 200.0, summary.avgWin, 0.01);
        assertEquals("Average loss should be $100", 100.0, summary.avgLoss, 0.01);
        assertEquals("Profit Factor should be 4.0 ($400 / $100)", 4.0, summary.profitFactor, 0.01);
    }

    @Test
    public void testRejectionForWaitAndNoTradeSignals() {
        DummySharedPreferences prefs = new DummySharedPreferences();

        PortfolioManager.RiskValidationResult valWait = PortfolioManager.validateTradeRisk(
                prefs, 2650.0, 2640.0, 2665.0, "WAIT ⏳", "ذكاء السوق"
        );
        assertFalse("WAIT signal should be rejected", valWait.isAllowed);
        assertTrue("Rejection message should mention WAIT signal in Arabic", valWait.messageArabic.contains("تحذر من التداول"));

        PortfolioManager.RiskValidationResult valNoTrade = PortfolioManager.validateTradeRisk(
                prefs, 2650.0, 2640.0, 2665.0, "NO TRADE 🚫", "ذكاء السوق"
        );
        assertFalse("NO TRADE signal should be rejected", valNoTrade.isAllowed);
        assertTrue("Rejection message should mention NO TRADE signal in Arabic", valNoTrade.messageArabic.contains("تحذر من التداول"));
    }

    private static class PortfolioTradeBuilder {
        private final PortfolioManager.PortfolioTrade t = new PortfolioManager.PortfolioTrade();

        public PortfolioTradeBuilder(String id, String type, double entry, double sl, double tp1, double pnl, String status, String source) {
            t.id = id;
            t.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
            t.symbol = "XAU/USD";
            t.type = type;
            t.entryPrice = entry;
            t.stopLoss = sl;
            t.tp1 = tp1;
            t.tp2 = tp1 + 10.0;
            t.lotSize = 0.1;
            t.riskAmount = 100.0;
            t.expectedProfit = 150.0;
            t.pnl = pnl;
            t.rrRatio = 1.5;
            t.status = status;
            t.notes = "اختبار نظام إدارة المخاطر";
            t.entryReason = "إشارة متوافقة مع شروط إدارة المخاطر";
            t.signalSource = source;
        }

        public PortfolioManager.PortfolioTrade build() { return t; }
    }

    private static class DummySharedPreferences implements android.content.SharedPreferences {
        private final Map<String, String> map = new HashMap<>();

        public DummySharedPreferences() {
            map.put(MainActivity.PREF_KEY_CAPITAL, "10000");
            map.put(MainActivity.PREF_KEY_RISK_PCT, "1.0");
            map.put(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0");
            map.put(PortfolioManager.PREF_KEY_MAX_DAILY_TRADES, "5");
        }

        public void putString(String key, String value) {
            map.put(key, value);
        }

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String defValue) { return map.getOrDefault(key, defValue); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return null; }
        @Override public int getInt(String key, int defValue) { return Integer.parseInt(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public long getLong(String key, long defValue) { return Long.parseLong(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public float getFloat(String key, float defValue) { return Float.parseFloat(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public boolean getBoolean(String key, boolean defValue) { return Boolean.parseBoolean(map.getOrDefault(key, String.valueOf(defValue))); }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return new DummyEditor(this); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static class DummyEditor implements Editor {
            private final DummySharedPreferences parent;
            private final Map<String, String> tempMap = new HashMap<>();

            public DummyEditor(DummySharedPreferences parent) { this.parent = parent; }
            @Override public Editor putString(String key, String value) { tempMap.put(key, value); return this; }
            @Override public Editor putStringSet(String key, java.util.Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { tempMap.put(key, String.valueOf(value)); return this; }
            @Override public Editor putLong(String key, long value) { tempMap.put(key, String.valueOf(value)); return this; }
            @Override public Editor putFloat(String key, float value) { tempMap.put(key, String.valueOf(value)); return this; }
            @Override public Editor putBoolean(String key, boolean value) { tempMap.put(key, String.valueOf(value)); return this; }
            @Override public Editor remove(String key) { tempMap.remove(key); parent.map.remove(key); return this; }
            @Override public Editor clear() { tempMap.clear(); parent.map.clear(); return this; }
            @Override public boolean commit() { parent.map.putAll(tempMap); return true; }
            @Override public void apply() { parent.map.putAll(tempMap); }
        }
    }
}
