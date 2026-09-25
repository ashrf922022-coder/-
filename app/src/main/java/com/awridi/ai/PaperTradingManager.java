package com.awridi.ai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * PaperTradingManager (Legacy Wrapper over PortfolioManager)
 * Retained for backward compatibility so no existing code or imports break.
 */
public class PaperTradingManager {
    public static final String PREF_KEY_PAPER_TRADES = "paper_trades_json";

    public static class PaperTrade {
        public String id, date, symbol, type, status, notes;
        public double entryPrice, stopLoss, tp1, tp2, pnl;
    }

    public static List<PaperTrade> loadPaperTrades(SharedPreferences prefs) {
        List<PortfolioManager.PortfolioTrade> pList = PortfolioManager.loadTrades(prefs);
        List<PaperTrade> list = new ArrayList<>();
        for (PortfolioManager.PortfolioTrade pt : pList) {
            PaperTrade t = new PaperTrade();
            t.id = pt.id;
            t.date = pt.date;
            t.symbol = pt.symbol;
            t.type = pt.type;
            t.entryPrice = pt.entryPrice;
            t.stopLoss = pt.stopLoss;
            t.tp1 = pt.tp1;
            t.tp2 = pt.tp2;
            t.status = pt.status;
            t.pnl = pt.pnl;
            t.notes = pt.notes;
            list.add(t);
        }
        return list;
    }

    public static void savePaperTrades(SharedPreferences prefs, List<PaperTrade> list) {
        List<PortfolioManager.PortfolioTrade> pList = PortfolioManager.loadTrades(prefs);
        for (PaperTrade t : list) {
            boolean found = false;
            for (PortfolioManager.PortfolioTrade pt : pList) {
                if (pt.id.equals(t.id)) {
                    pt.status = t.status;
                    pt.pnl = t.pnl;
                    pt.notes = t.notes;
                    found = true;
                    break;
                }
            }
            if (!found) {
                PortfolioManager.PortfolioTrade pt = new PortfolioManager.PortfolioTrade();
                pt.id = t.id;
                pt.date = t.date;
                pt.symbol = t.symbol;
                pt.type = t.type;
                pt.entryPrice = t.entryPrice;
                pt.stopLoss = t.stopLoss;
                pt.tp1 = t.tp1;
                pt.tp2 = t.tp2;
                pt.status = t.status;
                pt.pnl = t.pnl;
                pt.notes = t.notes;
                pList.add(pt);
            }
        }
        PortfolioManager.saveTrades(prefs, pList);
    }

    public static void executeTradeFromSignal(SharedPreferences prefs, GoldAnalysisEngine.AnalysisResult res) {
        PortfolioManager.executeTradeFromSignal(prefs, res);
    }

    public static void closeTrade(SharedPreferences prefs, PaperTrade trade, boolean isWin) {
        if (prefs == null || trade == null || trade.id == null) return;
        PaperTradeEngine engine = new PaperTradeEngine();
        double customPrice = isWin ? trade.tp1 : trade.stopLoss;
        engine.closePaperTrade(trade.id, customPrice, prefs);
    }
}
