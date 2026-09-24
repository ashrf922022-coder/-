package com.awridi.ai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 8: Backtesting & Strategy Validation Engine
 * Central engine for XAU/USD historical testing without Look-Ahead Bias.
 */
public class BacktestEngine {

    public static final String PREF_KEY_SAVED_BACKTESTS = "saved_backtests_json";

    public static class BacktestParams {
        public String symbol = MainActivity.GOLD_SYMBOL;
        public String timeframe = "15min";
        public double initialCapital = 10000.0;
        public double riskPerTradePct = 1.0;
        public double customPositionSizeLot = 0.0; // 0.0 = auto position sizing via RiskManagementEngine
        public double stopLossAtrMultiplier = 1.5;
        public double takeProfitAtrMultiplier = 2.25;
        public double commissionPerLot = 2.0; // $2 commission per lot traded
        public double spreadPips = 0.20; // $0.20 spread for XAU/USD (2 pips)
        public double slippagePips = 0.10; // $0.10 slippage for XAU/USD
        public String startDate = "";
        public String endDate = "";
        public double inSampleRatio = 1.0; // 1.0 = 100%, 0.7 = 70% In-Sample / 30% Out-Of-Sample

        @Override
        public String toString() {
            return "BacktestParams{" +
                    "symbol='" + symbol + '\'' +
                    ", timeframe='" + timeframe + '\'' +
                    ", capital=" + initialCapital +
                    ", riskPct=" + riskPerTradePct +
                    ", spread=" + spreadPips +
                    ", slippage=" + slippagePips +
                    ", commission=" + commissionPerLot +
                    '}';
        }
    }

    public static class BacktestTrade {
        public String id;
        public int tradeIndex;
        public int entryCandleIndex;
        public int exitCandleIndex;
        public String entryTime = "";
        public String exitTime = "";
        public String direction = "BUY"; // BUY or SELL
        public double entryPrice;
        public double exitPrice;
        public double stopLoss;
        public double takeProfit;
        public double lotSize;
        public double riskAmountUsd;
        public double riskPercentage;
        public double pnlUsd;
        public double pnlPercentage;
        public double commissionPaidUsd;
        public double spreadSlippageCostUsd;
        public String entryReason = "";
        public String exitReason = ""; // SL_HIT, TP_HIT, END_OF_DATA
        public String outcome = "BREAKEVEN"; // WIN, LOSS, BREAKEVEN
        public int durationBars;

        @Override
        public String toString() {
            return "BacktestTrade{" +
                    "id='" + id + '\'' +
                    ", dir='" + direction + '\'' +
                    ", entry=" + String.format(Locale.US, "%.2f", entryPrice) +
                    ", exit=" + String.format(Locale.US, "%.2f", exitPrice) +
                    ", lot=" + String.format(Locale.US, "%.2f", lotSize) +
                    ", pnl=" + String.format(Locale.US, "%+.2f", pnlUsd) +
                    ", outcome='" + outcome + '\'' +
                    '}';
        }
    }

    public static class EquityPoint {
        public int barIndex;
        public String timeStr = "";
        public double price;
        public double balance;
        public double equity;
        public double drawdownAmount;
        public double drawdownPct;
        public String associatedTradeId = "";

        public EquityPoint() {}

        public EquityPoint(int barIndex, String timeStr, double price, double balance, double equity, double drawdownAmount, double drawdownPct, String tradeId) {
            this.barIndex = barIndex;
            this.timeStr = timeStr;
            this.price = price;
            this.balance = balance;
            this.equity = equity;
            this.drawdownAmount = drawdownAmount;
            this.drawdownPct = drawdownPct;
            this.associatedTradeId = tradeId != null ? tradeId : "";
        }
    }

    public static class DrawdownAnalysis {
        public double maxDrawdownAmount = 0.0;
        public double maxDrawdownPct = 0.0;
        public String drawdownStartTime = "";
        public int drawdownStartBarIndex = -1;
        public double peakValue = 0.0;
        public String troughTime = "";
        public int troughBarIndex = -1;
        public double troughValue = 0.0;
        public String recoveryTime = "";
        public int recoveryBarIndex = -1;
        public int recoveryDurationBars = 0;
        public int longestLosingStreak = 0;

        @Override
        public String toString() {
            return "DrawdownAnalysis{" +
                    "maxDD=$" + String.format(Locale.US, "%.2f", maxDrawdownAmount) +
                    " (" + String.format(Locale.US, "%.2f%%", maxDrawdownPct) + ")" +
                    ", losingStreak=" + longestLosingStreak +
                    '}';
        }
    }

    public static class BacktestResult {
        public String runId = "";
        public String runTimestamp = "";
        public BacktestParams params = new BacktestParams();

        public double initialCapital = 10000.0;
        public double finalCapital = 10000.0;
        public double grossProfit = 0.0;
        public double grossLoss = 0.0;
        public double netPnl = 0.0;
        public double netPnlPct = 0.0;

        public int totalTrades = 0;
        public int winningTrades = 0;
        public int losingTrades = 0;
        public double winRate = 0.0; // 0.0 to 1.0
        public double lossRate = 0.0; // 0.0 to 1.0

        public double avgWin = 0.0;
        public double avgLoss = 0.0;
        public double profitFactor = 0.0;
        public double maxDrawdown = 0.0; // ratio (0.0 to 1.0)
        public double maxDrawdownPct = 0.0; // percentage (0.0 to 100.0)
        public double avgRiskReward = 0.0;
        public double largestWin = 0.0;
        public double largestLoss = 0.0;
        public double avgTradePnl = 0.0;
        public int maxConsecutiveWins = 0;
        public int longestLosingStreak = 0;

        public DrawdownAnalysis drawdownAnalysis = new DrawdownAnalysis();
        public List<EquityPoint> equityCurve = new ArrayList<>();
        public List<BacktestTrade> trades = new ArrayList<>();

        public String arabicSummary = "";

        @Override
        public String toString() {
            return "BacktestResult{" +
                    "trades=" + totalTrades +
                    ", winRate=" + String.format(Locale.US, "%.1f%%", winRate * 100) +
                    ", netPnl=$" + String.format(Locale.US, "%+.2f", netPnl) +
                    ", PF=" + String.format(Locale.US, "%.2f", profitFactor) +
                    ", maxDD=" + String.format(Locale.US, "%.1f%%", maxDrawdownPct) +
                    '}';
        }
    }

    public static class BacktestComparison {
        public List<BacktestResult> runs = new ArrayList<>();
        public String arabicComparisonTable = "";

        public BacktestComparison() {}

        public BacktestComparison(List<BacktestResult> runs) {
            this.runs = runs != null ? runs : new ArrayList<>();
            this.arabicComparisonTable = buildArabicComparisonTable(this.runs);
        }
    }

    /**
     * Central Backtest Execution method strictly preventing Look-Ahead Bias.
     */
    public static BacktestResult runBacktest(List<MarketIntelligenceEngine.Bar> bars, BacktestParams params) {
        BacktestResult result = new BacktestResult();
        if (params == null) params = new BacktestParams();
        result.params = params;
        result.initialCapital = params.initialCapital;
        result.runTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        result.runId = "BT_" + System.currentTimeMillis();

        if (bars == null || bars.size() < 30) {
            result.arabicSummary = "بيانات غير كافية لتشغيل الاختبار التاريخي (تتطلب 30 شمعة على الأقل).";
            result.finalCapital = params.initialCapital;
            return result;
        }

        // Handle In-Sample / Out-of-Sample slice if inSampleRatio < 1.0
        int effectiveBarCount = bars.size();
        if (params.inSampleRatio > 0.0 && params.inSampleRatio < 1.0) {
            effectiveBarCount = (int) Math.round(bars.size() * params.inSampleRatio);
            effectiveBarCount = Math.max(30, effectiveBarCount);
        }

        double cash = params.initialCapital;
        double peakCapital = cash;
        double maxDDAmount = 0.0;
        double maxDDPct = 0.0;

        int wins = 0, losses = 0;
        int currentWinStreak = 0, maxWinStreak = 0;
        int currentLossStreak = 0, maxLossStreak = 0;
        double totalRRSum = 0.0;

        DrawdownAnalysis ddAnalysis = new DrawdownAnalysis();
        ddAnalysis.peakValue = cash;
        int currentDdStartIdx = -1;

        // Initial Equity Point
        result.equityCurve.add(new EquityPoint(0, "Bar_0", bars.get(0).close, cash, cash, 0.0, 0.0, ""));

        SignalEngine signalEngine = new SignalEngine();
        RiskManagementEngine riskEngine = new RiskManagementEngine();

        // Main Simulation Loop with STRICT ZERO LOOK-AHEAD BIAS
        for (int i = 29; i < effectiveBarCount - 1; i++) {
            MarketIntelligenceEngine.Bar currentBar = bars.get(i);

            // 1. Evaluate signal strictly at candle index `i` (using bars 0..i only)
            SignalEngine.SignalResult sigResult = signalEngine.generateSignalAtCandle(bars, i);

            if (sigResult != null && sigResult.valid && sigResult.signalType != SignalEngine.SignalType.HOLD) {
                // Determine Trade Direction
                TradeSetup.Direction dir = sigResult.signalType == SignalEngine.SignalType.BUY ?
                        TradeSetup.Direction.BUY : TradeSetup.Direction.SELL;

                // Position Sizing calculation
                double lotSize = params.customPositionSizeLot;
                double riskAmountUsd = cash * (params.riskPerTradePct / 100.0);

                if (lotSize <= 0.0) {
                    double rawSL = sigResult.suggestedStopLoss > 0 ? sigResult.suggestedStopLoss :
                            (dir == TradeSetup.Direction.BUY ? currentBar.close - 5.0 : currentBar.close + 5.0);
                    double rawTP = sigResult.suggestedTakeProfit > 0 ? sigResult.suggestedTakeProfit :
                            (dir == TradeSetup.Direction.BUY ? currentBar.close + 7.5 : currentBar.close - 7.5);

                    RiskManagementEngine.RiskResult riskRes = riskEngine.evaluateRisk(
                            cash, params.riskPerTradePct, currentBar.close, rawSL, rawTP, dir, 0.0
                    );
                    if (riskRes.valid && riskRes.positionSize > 0) {
                        lotSize = riskRes.positionSize;
                        riskAmountUsd = riskRes.riskAmount;
                    } else {
                        lotSize = Math.max(0.01, (riskAmountUsd) / (1.5 * 100.0));
                    }
                }

                lotSize = Math.max(0.01, lotSize);

                // 2. Realistic Execution: Spread, Slippage & Commission
                double spreadCost = params.spreadPips;
                double slippageCost = params.slippagePips;
                double totalFrictionPerPip = (spreadCost / 2.0) + slippageCost;
                double totalSpreadSlippageUsd = (spreadCost + (slippageCost * 2.0)) * lotSize * 100.0;
                double commissionUsd = params.commissionPerLot * lotSize;

                double entryPrice;
                double sl;
                double tp;

                if (dir == TradeSetup.Direction.BUY) {
                    entryPrice = currentBar.close + totalFrictionPerPip;
                    double distSL = Math.max(0.5, currentBar.close - (sigResult.suggestedStopLoss > 0 ? sigResult.suggestedStopLoss : currentBar.close - 5.0));
                    double distTP = Math.max(1.0, (sigResult.suggestedTakeProfit > 0 ? sigResult.suggestedTakeProfit : currentBar.close + 7.5) - currentBar.close);

                    sl = entryPrice - distSL;
                    tp = entryPrice + distTP;
                } else { // SELL
                    entryPrice = currentBar.close - totalFrictionPerPip;
                    double distSL = Math.max(0.5, (sigResult.suggestedStopLoss > 0 ? sigResult.suggestedStopLoss : currentBar.close + 5.0) - currentBar.close);
                    double distTP = Math.max(1.0, currentBar.close - (sigResult.suggestedTakeProfit > 0 ? sigResult.suggestedTakeProfit : currentBar.close - 7.5));

                    sl = entryPrice + distSL;
                    tp = entryPrice - distTP;
                }

                double riskDist = Math.abs(entryPrice - sl);
                double rewardDist = Math.abs(tp - entryPrice);
                double rrRatio = riskDist > 0 ? rewardDist / riskDist : 1.5;
                totalRRSum += rrRatio;

                BacktestTrade trade = new BacktestTrade();
                trade.id = "T_" + (result.trades.size() + 1);
                trade.tradeIndex = result.trades.size() + 1;
                trade.entryCandleIndex = i;
                trade.entryTime = "Bar_" + i;
                trade.direction = dir.name();
                trade.entryPrice = entryPrice;
                trade.stopLoss = sl;
                trade.takeProfit = tp;
                trade.lotSize = lotSize;
                trade.riskAmountUsd = riskAmountUsd;
                trade.riskPercentage = params.riskPerTradePct;
                trade.commissionPaidUsd = commissionUsd;
                trade.spreadSlippageCostUsd = totalSpreadSlippageUsd;
                trade.entryReason = sigResult.signalReason != null ? sigResult.signalReason : "إشارة تداول فنية";

                // 3. Forward Simulation
                TradePosition pos = new TradePosition();
                pos.tradeId = trade.id;
                pos.direction = dir == TradeSetup.Direction.BUY ? TradePosition.Direction.BUY : TradePosition.Direction.SELL;
                pos.status = TradePosition.Status.OPEN;
                pos.entryPrice = entryPrice;
                pos.stopLoss = sl;
                pos.takeProfit = tp;
                pos.positionSize = lotSize;
                pos.riskAmount = riskAmountUsd;

                TradeMonitorEngine monitorEngine = new TradeMonitorEngine();

                boolean tradeClosed = false;
                for (int j = i + 1; j < effectiveBarCount; j++) {
                    MarketIntelligenceEngine.Bar futureBar = bars.get(j);
                    TradeMonitorEngine.MonitoringResult monRes = monitorEngine.monitorBar(pos, futureBar, cash);

                    if (monRes.statusChanged && (pos.status == TradePosition.Status.TP_HIT || pos.status == TradePosition.Status.SL_HIT)) {
                        trade.exitCandleIndex = j;
                        trade.exitTime = "Bar_" + j;
                        trade.exitPrice = pos.currentPrice;
                        trade.pnlUsd = pos.realizedPnL - commissionUsd;
                        trade.pnlPercentage = cash > 0 ? (trade.pnlUsd / cash) * 100.0 : 0.0;
                        trade.exitReason = pos.status.name() + (pos.conflictType.equals("TP_SL_CONFLICT") ? "_CONFLICT" : "");
                        trade.outcome = trade.pnlUsd >= 0 ? "WIN" : "LOSS";
                        trade.durationBars = j - i;
                        tradeClosed = true;
                        i = j;
                        break;
                    }
                }

                if (!tradeClosed) {
                    int lastIdx = effectiveBarCount - 1;
                    MarketIntelligenceEngine.Bar lastBar = bars.get(lastIdx);
                    trade.exitCandleIndex = lastIdx;
                    trade.exitTime = "Bar_" + lastIdx;
                    trade.exitPrice = lastBar.close;
                    double rawPnl = dir == TradeSetup.Direction.BUY ?
                            (trade.exitPrice - trade.entryPrice) * lotSize * 100.0 :
                            (trade.entryPrice - trade.exitPrice) * lotSize * 100.0;
                    trade.pnlUsd = rawPnl - commissionUsd;
                    trade.pnlPercentage = (trade.pnlUsd / cash) * 100.0;
                    trade.exitReason = "END_OF_DATA";
                    trade.outcome = trade.pnlUsd >= 0 ? "WIN" : "LOSS";
                    trade.durationBars = lastIdx - i;
                    i = lastIdx;
                }

                // Update Cash & Statistics
                cash += trade.pnlUsd;
                result.trades.add(trade);
                result.totalTrades++;

                if (trade.pnlUsd > 0) {
                    wins++;
                    result.grossProfit += trade.pnlUsd;
                    if (trade.pnlUsd > result.largestWin) result.largestWin = trade.pnlUsd;
                    currentWinStreak++;
                    if (currentWinStreak > maxWinStreak) maxWinStreak = currentWinStreak;
                    currentLossStreak = 0;
                } else {
                    losses++;
                    double absLoss = Math.abs(trade.pnlUsd);
                    result.grossLoss += absLoss;
                    if (absLoss > result.largestLoss) result.largestLoss = absLoss;
                    currentLossStreak++;
                    if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                    currentWinStreak = 0;
                }

                // Drawdown calculations
                if (cash > peakCapital) {
                    if (currentDdStartIdx != -1) {
                        ddAnalysis.recoveryTime = trade.exitTime;
                        ddAnalysis.recoveryBarIndex = trade.exitCandleIndex;
                        ddAnalysis.recoveryDurationBars = trade.exitCandleIndex - currentDdStartIdx;
                        currentDdStartIdx = -1;
                    }
                    peakCapital = cash;
                } else {
                    if (currentDdStartIdx == -1) {
                        currentDdStartIdx = trade.entryCandleIndex;
                        ddAnalysis.drawdownStartTime = trade.entryTime;
                        ddAnalysis.drawdownStartBarIndex = trade.entryCandleIndex;
                    }
                    double ddVal = peakCapital - cash;
                    double ddPctVal = peakCapital > 0 ? (ddVal / peakCapital) * 100.0 : 0.0;
                    if (ddVal > maxDDAmount) {
                        maxDDAmount = ddVal;
                        maxDDPct = ddPctVal;
                        ddAnalysis.maxDrawdownAmount = ddVal;
                        ddAnalysis.maxDrawdownPct = ddPctVal;
                        ddAnalysis.troughTime = trade.exitTime;
                        ddAnalysis.troughBarIndex = trade.exitCandleIndex;
                        ddAnalysis.troughValue = cash;
                    }
                }

                double curDDPct = peakCapital > 0 ? ((peakCapital - cash) / peakCapital) * 100.0 : 0.0;
                result.equityCurve.add(new EquityPoint(i, "Bar_" + i, currentBar.close, cash, cash, peakCapital - cash, curDDPct, trade.id));
            } else {
                double curDDPct = peakCapital > 0 ? ((peakCapital - cash) / peakCapital) * 100.0 : 0.0;
                result.equityCurve.add(new EquityPoint(i, "Bar_" + i, currentBar.close, cash, cash, peakCapital - cash, curDDPct, ""));
            }
        }

        // Finalize Result Metrics
        result.finalCapital = cash;
        result.netPnl = result.grossProfit - result.grossLoss;
        result.netPnlPct = params.initialCapital > 0 ? (result.netPnl / params.initialCapital) * 100.0 : 0.0;
        result.winningTrades = wins;
        result.losingTrades = losses;
        result.winRate = result.totalTrades > 0 ? (double) wins / result.totalTrades : 0.0;
        result.lossRate = result.totalTrades > 0 ? (double) losses / result.totalTrades : 0.0;
        result.avgWin = wins > 0 ? result.grossProfit / wins : 0.0;
        result.avgLoss = losses > 0 ? result.grossLoss / losses : 0.0;
        result.profitFactor = result.grossLoss > 0 ? result.grossProfit / result.grossLoss : (result.grossProfit > 0 ? 99.0 : 0.0);
        result.maxDrawdownPct = maxDDPct;
        result.maxDrawdown = maxDDPct / 100.0;
        result.avgRiskReward = result.totalTrades > 0 ? totalRRSum / result.totalTrades : 0.0;
        result.avgTradePnl = result.totalTrades > 0 ? result.netPnl / result.totalTrades : 0.0;
        result.maxConsecutiveWins = maxWinStreak;
        result.longestLosingStreak = maxLossStreak;

        ddAnalysis.longestLosingStreak = maxLossStreak;
        ddAnalysis.peakValue = peakCapital;
        result.drawdownAnalysis = ddAnalysis;

        result.arabicSummary = generateArabicSummary(result);
        return result;
    }

    /**
     * Splits data into In-Sample and Out-of-Sample subsets.
     */
    public static List<List<MarketIntelligenceEngine.Bar>> splitData(List<MarketIntelligenceEngine.Bar> bars, double inSampleRatio) {
        List<List<MarketIntelligenceEngine.Bar>> split = new ArrayList<>();
        if (bars == null || bars.isEmpty()) {
            split.add(new ArrayList<>());
            split.add(new ArrayList<>());
            return split;
        }

        double ratio = Math.max(0.1, Math.min(0.9, inSampleRatio));
        int splitIndex = (int) Math.round(bars.size() * ratio);

        List<MarketIntelligenceEngine.Bar> inSample = new ArrayList<>(bars.subList(0, splitIndex));
        List<MarketIntelligenceEngine.Bar> outOfSample = new ArrayList<>(bars.subList(splitIndex, bars.size()));

        split.add(inSample);
        split.add(outOfSample);
        return split;
    }

    // --- LOCAL STORAGE & SIDE-BY-SIDE COMPARISON ---
    public static void saveBacktestRun(SharedPreferences prefs, BacktestResult result) {
        if (prefs == null || result == null) return;
        List<BacktestResult> existing = loadBacktestRuns(prefs);

        // Replace if runId exists
        boolean found = false;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).runId.equals(result.runId)) {
                existing.set(i, result);
                found = true;
                break;
            }
        }
        if (!found) {
            existing.add(result);
        }

        saveBacktestRunsList(prefs, existing);
    }

    public static List<BacktestResult> loadBacktestRuns(SharedPreferences prefs) {
        List<BacktestResult> list = new ArrayList<>();
        if (prefs == null) return list;

        try {
            String jsonStr = prefs.getString(PREF_KEY_SAVED_BACKTESTS, "[]");
            if (jsonStr == null || jsonStr.trim().isEmpty() || jsonStr.trim().equals("[]")) {
                return list;
            }

            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    BacktestResult res = new BacktestResult();
                    res.runId = obj.optString("runId", "BT_" + i);
                    res.runTimestamp = obj.optString("runTimestamp", "");
                    res.initialCapital = obj.optDouble("initialCapital", 10000.0);
                    res.finalCapital = obj.optDouble("finalCapital", 10000.0);
                    res.netPnl = obj.optDouble("netPnl", 0.0);
                    res.netPnlPct = obj.optDouble("netPnlPct", 0.0);
                    res.totalTrades = obj.optInt("totalTrades", 0);
                    res.winningTrades = obj.optInt("winningTrades", 0);
                    res.losingTrades = obj.optInt("losingTrades", 0);
                    res.winRate = obj.optDouble("winRate", 0.0);
                    res.profitFactor = obj.optDouble("profitFactor", 0.0);
                    res.maxDrawdownPct = obj.optDouble("maxDrawdownPct", 0.0);
                    res.maxDrawdown = res.maxDrawdownPct / 100.0;
                    res.avgTradePnl = obj.optDouble("avgTradePnl", 0.0);
                    res.avgRiskReward = obj.optDouble("avgRiskReward", 0.0);

                    JSONObject pObj = obj.optJSONObject("params");
                    if (pObj != null) {
                        res.params.timeframe = pObj.optString("timeframe", "15min");
                        res.params.riskPerTradePct = pObj.optDouble("riskPerTradePct", 1.0);
                        res.params.spreadPips = pObj.optDouble("spreadPips", 0.20);
                        res.params.slippagePips = pObj.optDouble("slippagePips", 0.10);
                        res.params.commissionPerLot = pObj.optDouble("commissionPerLot", 2.0);
                    }
                    list.add(res);
                }
            } catch (Throwable unmockedError) {
                // JVM Fallback parser
                return parseBacktestsFallback(jsonStr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static List<BacktestResult> parseBacktestsFallback(String jsonStr) {
        List<BacktestResult> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(jsonStr);
        while (m.find()) {
            String block = m.group();
            BacktestResult res = new BacktestResult();
            res.runId = optStringRegex(block, "runId", "BT_" + System.currentTimeMillis());
            res.runTimestamp = optStringRegex(block, "runTimestamp", "");
            res.initialCapital = optDoubleRegex(block, "initialCapital", 10000.0);
            res.finalCapital = optDoubleRegex(block, "finalCapital", 10000.0);
            res.netPnl = optDoubleRegex(block, "netPnl", 0.0);
            res.netPnlPct = optDoubleRegex(block, "netPnlPct", 0.0);
            res.totalTrades = (int) optDoubleRegex(block, "totalTrades", 0);
            res.winningTrades = (int) optDoubleRegex(block, "winningTrades", 0);
            res.losingTrades = (int) optDoubleRegex(block, "losingTrades", 0);
            res.winRate = optDoubleRegex(block, "winRate", 0.0);
            res.profitFactor = optDoubleRegex(block, "profitFactor", 0.0);
            res.maxDrawdownPct = optDoubleRegex(block, "maxDrawdownPct", 0.0);
            res.maxDrawdown = res.maxDrawdownPct / 100.0;
            res.avgTradePnl = optDoubleRegex(block, "avgTradePnl", 0.0);
            res.avgRiskReward = optDoubleRegex(block, "avgRiskReward", 0.0);
            list.add(res);
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

    private static void saveBacktestRunsList(SharedPreferences prefs, List<BacktestResult> list) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                BacktestResult r = list.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"runId\":\"").append(r.runId).append("\",")
                  .append("\"runTimestamp\":\"").append(r.runTimestamp).append("\",")
                  .append("\"initialCapital\":").append(String.format(Locale.US, "%.2f", r.initialCapital)).append(",")
                  .append("\"finalCapital\":").append(String.format(Locale.US, "%.2f", r.finalCapital)).append(",")
                  .append("\"netPnl\":").append(String.format(Locale.US, "%.2f", r.netPnl)).append(",")
                  .append("\"netPnlPct\":").append(String.format(Locale.US, "%.2f", r.netPnlPct)).append(",")
                  .append("\"totalTrades\":").append(r.totalTrades).append(",")
                  .append("\"winningTrades\":").append(r.winningTrades).append(",")
                  .append("\"losingTrades\":").append(r.losingTrades).append(",")
                  .append("\"winRate\":").append(String.format(Locale.US, "%.4f", r.winRate)).append(",")
                  .append("\"profitFactor\":").append(String.format(Locale.US, "%.2f", r.profitFactor)).append(",")
                  .append("\"maxDrawdownPct\":").append(String.format(Locale.US, "%.2f", r.maxDrawdownPct)).append(",")
                  .append("\"avgTradePnl\":").append(String.format(Locale.US, "%.2f", r.avgTradePnl)).append(",")
                  .append("\"avgRiskReward\":").append(String.format(Locale.US, "%.2f", r.avgRiskReward)).append(",")
                  .append("\"params\":{")
                  .append("\"timeframe\":\"").append(r.params.timeframe).append("\",")
                  .append("\"riskPerTradePct\":").append(String.format(Locale.US, "%.2f", r.params.riskPerTradePct)).append(",")
                  .append("\"spreadPips\":").append(String.format(Locale.US, "%.2f", r.params.spreadPips)).append(",")
                  .append("\"slippagePips\":").append(String.format(Locale.US, "%.2f", r.params.slippagePips)).append(",")
                  .append("\"commissionPerLot\":").append(String.format(Locale.US, "%.2f", r.params.commissionPerLot))
                  .append("}")
                  .append("}");
            }
            sb.append("]");
            prefs.edit().putString(PREF_KEY_SAVED_BACKTESTS, sb.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String buildArabicComparisonTable(List<BacktestResult> runs) {
        if (runs == null || runs.isEmpty()) {
            return "لا توجد اختبارات محفوظة لمقارنتها.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **مقارنة الاختبارات التاريخية جنبًا إلى جنب (Side-by-Side Comparison)**\n\n");

        for (int i = 0; i < runs.size(); i++) {
            BacktestResult r = runs.get(i);
            sb.append("🔹 **اختبار #").append(i + 1).append(" (").append(r.runId).append(") - ").append(r.runTimestamp).append("**\n");
            sb.append(" - صافي الأرباح (Net Profit): $").append(String.format(Locale.US, "%+.2f", r.netPnl)).append(" (").append(String.format(Locale.US, "%+.2f%%", r.netPnlPct)).append(")\n");
            sb.append(" - نسبة النجاح (Win Rate): ").append(String.format(Locale.US, "%.1f%%", r.winRate * 100)).append("\n");
            sb.append(" - معامل الربحية (Profit Factor): ").append(String.format(Locale.US, "%.2f", r.profitFactor)).append("\n");
            sb.append(" - أقصى انخفاض (Max Drawdown): ").append(String.format(Locale.US, "%.2f%%", r.maxDrawdownPct)).append("\n");
            sb.append(" - إجمالي الصفقات (Total Trades): ").append(r.totalTrades).append("\n");
            sb.append(" - متوسط الربح لكل صفقة (Avg Trade): $").append(String.format(Locale.US, "%+.2f", r.avgTradePnl)).append("\n");
            sb.append(" - متوسط نسبة المخاطرة للعائد (Risk/Reward): 1 : ").append(String.format(Locale.US, "%.2f", r.avgRiskReward)).append("\n\n");
        }

        sb.append("📌 *ملاحظة: البيانات معروضة فقط للمارنة ولا يتم إعطاء أفضلية أو ترشيح تلقائي لأي استراتيجية.*");
        return sb.toString();
    }

    private static String generateArabicSummary(BacktestResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **نتائج الاختبار التاريخي لصفقات الذهب (XAU/USD)**\n");
        sb.append("• رأس المال الابتدائي: $").append(String.format(Locale.US, "%.2f", res.initialCapital)).append("\n");
        sb.append("• الرصيد النهائي: $").append(String.format(Locale.US, "%.2f", res.finalCapital)).append("\n");
        sb.append("• صافي الأرباح: $").append(String.format(Locale.US, "%+.2f", res.netPnl)).append(" (").append(String.format(Locale.US, "%+.2f%%", res.netPnlPct)).append(")\n");
        sb.append("• إجمالي الصفقات: ").append(res.totalTrades).append(" (الصفقات الرابحة: ").append(res.winningTrades).append(" | الصفقات الخاسرة: ").append(res.losingTrades).append(")\n");
        sb.append("• نسبة النجاح (Win Rate): ").append(String.format(Locale.US, "%.1f%%", res.winRate * 100)).append("\n");
        sb.append("• معامل الربحية (Profit Factor): ").append(String.format(Locale.US, "%.2f", res.profitFactor)).append("\n");
        sb.append("• أقصى انخفاض (Max Drawdown): $").append(String.format(Locale.US, "%.2f", res.drawdownAnalysis.maxDrawdownAmount)).append(" (").append(String.format(Locale.US, "%.2f%%", res.maxDrawdownPct)).append(")\n");
        sb.append("• متوسط الصفقة الرابحة: $").append(String.format(Locale.US, "%.2f", res.avgWin)).append("\n");
        sb.append("• متوسط الصفقة الخاسرة: $").append(String.format(Locale.US, "%.2f", res.avgLoss)).append("\n");
        sb.append("• متوسط الربح/الخسارة لكل صفقة: $").append(String.format(Locale.US, "%+.2f", res.avgTradePnl)).append("\n");
        sb.append("• أكبر صفقة رابحة: $").append(String.format(Locale.US, "%.2f", res.largestWin)).append("\n");
        sb.append("• أكبر صفقة خاسرة: $").append(String.format(Locale.US, "%.2f", res.largestLoss)).append("\n");
        sb.append("• أطول سلسلة خسائر متتالية: ").append(res.longestLosingStreak).append("\n");
        sb.append("• متوسط نسبة المخاطرة إلى العائد (R:R): 1 : ").append(String.format(Locale.US, "%.2f", res.avgRiskReward)).append("\n");
        return sb.toString();
    }

    // --- Backward Compatibility Methods ---
    public static BacktestResult runGoldBacktest(List<GoldAnalysisEngine.Bar> bars, SharedPreferences prefs) {
        List<MarketIntelligenceEngine.Bar> miBars = new ArrayList<>();
        if (bars != null) {
            for (GoldAnalysisEngine.Bar b : bars) {
                miBars.add(new MarketIntelligenceEngine.Bar(b.o, b.h, b.l, b.c, b.v));
            }
        }
        return runMarketIntelligenceBacktest(miBars, prefs);
    }

    public static BacktestResult runTradingDecisionBacktest(List<MarketIntelligenceEngine.Bar> bars, SharedPreferences prefs) {
        BacktestParams params = new BacktestParams();
        if (prefs != null) {
            try {
                params.initialCapital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
                params.riskPerTradePct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
            } catch (Exception ignored) {}
        }
        return runBacktest(bars, params);
    }

    public static BacktestResult runMarketIntelligenceBacktest(List<MarketIntelligenceEngine.Bar> miBars, SharedPreferences prefs) {
        return runTradingDecisionBacktest(miBars, prefs);
    }

    /**
     * Backtest Execution using AIDecisionEngine candle-by-candle with strict zero Look-Ahead Bias.
     */
    /**
     * Executes paper trade backtest pipeline using PaperTradeEngine, AIDecisionEngine, and TradeMonitorEngine
     * with strict zero Look-Ahead Bias and conservative TP/SL conflict policy.
     */
    public static BacktestResult runPaperTradeBacktest(List<MarketIntelligenceEngine.Bar> bars, BacktestParams params, SharedPreferences prefs) {
        return runAIBacktest(bars, params, prefs);
    }

    public static BacktestResult runAIBacktest(List<MarketIntelligenceEngine.Bar> bars, BacktestParams params, SharedPreferences prefs) {
        BacktestResult result = new BacktestResult();
        if (params == null) params = new BacktestParams();
        result.params = params;
        result.initialCapital = params.initialCapital;
        result.runTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        result.runId = "AI_BT_" + System.currentTimeMillis();

        if (bars == null || bars.size() < 30) {
            result.arabicSummary = "بيانات غير كافية لتشغيل اختبار المحرك الذكي (تتطلب 30 شمعة على الأقل).";
            result.finalCapital = params.initialCapital;
            return result;
        }

        int effectiveBarCount = bars.size();
        if (params.inSampleRatio > 0.0 && params.inSampleRatio < 1.0) {
            effectiveBarCount = (int) Math.round(bars.size() * params.inSampleRatio);
            effectiveBarCount = Math.max(30, effectiveBarCount);
        }

        double cash = params.initialCapital;
        double peakCapital = cash;
        double maxDDAmount = 0.0;
        double maxDDPct = 0.0;

        int wins = 0, losses = 0;
        int currentWinStreak = 0, maxWinStreak = 0;
        int currentLossStreak = 0, maxLossStreak = 0;
        double totalRRSum = 0.0;

        DrawdownAnalysis ddAnalysis = new DrawdownAnalysis();
        ddAnalysis.peakValue = cash;
        int currentDdStartIdx = -1;

        result.equityCurve.add(new EquityPoint(0, "Bar_0", bars.get(0).close, cash, cash, 0.0, 0.0, ""));

        AIDecisionEngine aiEngine = new AIDecisionEngine();

        for (int i = 29; i < effectiveBarCount - 1; i++) {
            MarketIntelligenceEngine.Bar currentBar = bars.get(i);

            // Evaluate AI Decision strictly at candle index i (using bars 0..i only)
            AIDecisionResult aiRes = aiEngine.evaluateAtCandle(bars, i, params.symbol, params.timeframe, prefs);

            if (aiRes != null && (aiRes.decision == AIDecisionResult.Decision.BUY || aiRes.decision == AIDecisionResult.Decision.SELL)) {
                TradeSetup.Direction dir = aiRes.decision == AIDecisionResult.Decision.BUY ?
                        TradeSetup.Direction.BUY : TradeSetup.Direction.SELL;

                double lotSize = params.customPositionSizeLot > 0 ? params.customPositionSizeLot :
                        (aiRes.positionSizeLot > 0 ? aiRes.positionSizeLot : 0.1);
                lotSize = Math.max(0.01, lotSize);

                double riskAmountUsd = cash * (params.riskPerTradePct / 100.0);

                double spreadCost = params.spreadPips;
                double slippageCost = params.slippagePips;
                double totalFrictionPerPip = (spreadCost / 2.0) + slippageCost;
                double totalSpreadSlippageUsd = (spreadCost + (slippageCost * 2.0)) * lotSize * 100.0;
                double commissionUsd = params.commissionPerLot * lotSize;

                double entryPrice = dir == TradeSetup.Direction.BUY ?
                        currentBar.close + totalFrictionPerPip : currentBar.close - totalFrictionPerPip;

                double sl = aiRes.stopLoss > 0 ? aiRes.stopLoss :
                        (dir == TradeSetup.Direction.BUY ? entryPrice - 5.0 : entryPrice + 5.0);
                double tp = aiRes.takeProfit > 0 ? aiRes.takeProfit :
                        (dir == TradeSetup.Direction.BUY ? entryPrice + 10.0 : entryPrice - 10.0);

                double riskDist = Math.abs(entryPrice - sl);
                double rewardDist = Math.abs(tp - entryPrice);
                double rrRatio = riskDist > 0 ? rewardDist / riskDist : 2.0;
                totalRRSum += rrRatio;

                BacktestTrade trade = new BacktestTrade();
                trade.id = "AI_T_" + (result.trades.size() + 1);
                trade.tradeIndex = result.trades.size() + 1;
                trade.entryCandleIndex = i;
                trade.entryTime = "Bar_" + i;
                trade.direction = dir.name();
                trade.entryPrice = entryPrice;
                trade.stopLoss = sl;
                trade.takeProfit = tp;
                trade.lotSize = lotSize;
                trade.riskAmountUsd = riskAmountUsd;
                trade.riskPercentage = params.riskPerTradePct;
                trade.commissionPaidUsd = commissionUsd;
                trade.spreadSlippageCostUsd = totalSpreadSlippageUsd;
                trade.entryReason = "AI Decision: " + aiRes.decision.name() + " (Quality: " + aiRes.tradeQuality.name() + ")";

                TradePosition pos = new TradePosition();
                pos.tradeId = trade.id;
                pos.direction = dir == TradeSetup.Direction.BUY ? TradePosition.Direction.BUY : TradePosition.Direction.SELL;
                pos.status = TradePosition.Status.OPEN;
                pos.entryPrice = entryPrice;
                pos.stopLoss = sl;
                pos.takeProfit = tp;
                pos.positionSize = lotSize;
                pos.riskAmount = riskAmountUsd;

                TradeMonitorEngine monitorEngine = new TradeMonitorEngine();

                boolean tradeClosed = false;
                for (int j = i + 1; j < effectiveBarCount; j++) {
                    MarketIntelligenceEngine.Bar futureBar = bars.get(j);
                    TradeMonitorEngine.MonitoringResult monRes = monitorEngine.monitorBar(pos, futureBar, cash);

                    if (monRes.statusChanged && (pos.status == TradePosition.Status.TP_HIT || pos.status == TradePosition.Status.SL_HIT)) {
                        trade.exitCandleIndex = j;
                        trade.exitTime = "Bar_" + j;
                        trade.exitPrice = pos.currentPrice;
                        trade.pnlUsd = pos.realizedPnL - commissionUsd;
                        trade.pnlPercentage = cash > 0 ? (trade.pnlUsd / cash) * 100.0 : 0.0;
                        trade.exitReason = pos.status.name() + (pos.conflictType.equals("TP_SL_CONFLICT") ? "_CONFLICT" : "");
                        trade.outcome = trade.pnlUsd >= 0 ? "WIN" : "LOSS";
                        trade.durationBars = j - i;
                        tradeClosed = true;
                        i = j;
                        break;
                    }
                }

                if (!tradeClosed) {
                    int lastIdx = effectiveBarCount - 1;
                    MarketIntelligenceEngine.Bar lastBar = bars.get(lastIdx);
                    trade.exitCandleIndex = lastIdx;
                    trade.exitTime = "Bar_" + lastIdx;
                    trade.exitPrice = lastBar.close;
                    double rawPnl = dir == TradeSetup.Direction.BUY ?
                            (trade.exitPrice - trade.entryPrice) * lotSize * 100.0 :
                            (trade.entryPrice - trade.exitPrice) * lotSize * 100.0;
                    trade.pnlUsd = rawPnl - commissionUsd;
                    trade.pnlPercentage = (trade.pnlUsd / cash) * 100.0;
                    trade.exitReason = "END_OF_DATA";
                    trade.outcome = trade.pnlUsd >= 0 ? "WIN" : "LOSS";
                    trade.durationBars = lastIdx - i;
                    i = lastIdx;
                }

                cash += trade.pnlUsd;
                result.trades.add(trade);
                result.totalTrades++;

                if (trade.pnlUsd > 0) {
                    wins++;
                    result.grossProfit += trade.pnlUsd;
                    if (trade.pnlUsd > result.largestWin) result.largestWin = trade.pnlUsd;
                    currentWinStreak++;
                    if (currentWinStreak > maxWinStreak) maxWinStreak = currentWinStreak;
                    currentLossStreak = 0;
                } else {
                    losses++;
                    double absLoss = Math.abs(trade.pnlUsd);
                    result.grossLoss += absLoss;
                    if (absLoss > result.largestLoss) result.largestLoss = absLoss;
                    currentLossStreak++;
                    if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                    currentWinStreak = 0;
                }

                if (cash > peakCapital) {
                    if (currentDdStartIdx != -1) {
                        ddAnalysis.recoveryTime = trade.exitTime;
                        ddAnalysis.recoveryBarIndex = trade.exitCandleIndex;
                        ddAnalysis.recoveryDurationBars = trade.exitCandleIndex - currentDdStartIdx;
                        currentDdStartIdx = -1;
                    }
                    peakCapital = cash;
                } else {
                    if (currentDdStartIdx == -1) {
                        currentDdStartIdx = trade.entryCandleIndex;
                        ddAnalysis.drawdownStartTime = trade.entryTime;
                        ddAnalysis.drawdownStartBarIndex = trade.entryCandleIndex;
                    }
                    double ddVal = peakCapital - cash;
                    double ddPctVal = peakCapital > 0 ? (ddVal / peakCapital) * 100.0 : 0.0;
                    if (ddVal > maxDDAmount) {
                        maxDDAmount = ddVal;
                        maxDDPct = ddPctVal;
                        ddAnalysis.maxDrawdownAmount = ddVal;
                        ddAnalysis.maxDrawdownPct = ddPctVal;
                        ddAnalysis.troughTime = trade.exitTime;
                        ddAnalysis.troughBarIndex = trade.exitCandleIndex;
                        ddAnalysis.troughValue = cash;
                    }
                }

                double curDDPct = peakCapital > 0 ? ((peakCapital - cash) / peakCapital) * 100.0 : 0.0;
                result.equityCurve.add(new EquityPoint(i, "Bar_" + i, currentBar.close, cash, cash, peakCapital - cash, curDDPct, trade.id));
            } else {
                double curDDPct = peakCapital > 0 ? ((peakCapital - cash) / peakCapital) * 100.0 : 0.0;
                result.equityCurve.add(new EquityPoint(i, "Bar_" + i, currentBar.close, cash, cash, peakCapital - cash, curDDPct, ""));
            }
        }

        result.finalCapital = cash;
        result.netPnl = result.grossProfit - result.grossLoss;
        result.netPnlPct = params.initialCapital > 0 ? (result.netPnl / params.initialCapital) * 100.0 : 0.0;
        result.winningTrades = wins;
        result.losingTrades = losses;
        result.winRate = result.totalTrades > 0 ? (double) wins / result.totalTrades : 0.0;
        result.lossRate = result.totalTrades > 0 ? (double) losses / result.totalTrades : 0.0;
        result.avgWin = wins > 0 ? result.grossProfit / wins : 0.0;
        result.avgLoss = losses > 0 ? result.grossLoss / losses : 0.0;
        result.profitFactor = result.grossLoss > 0 ? result.grossProfit / result.grossLoss : (result.grossProfit > 0 ? 99.0 : 0.0);
        result.maxDrawdownPct = maxDDPct;
        result.maxDrawdown = maxDDPct / 100.0;
        result.avgRiskReward = result.totalTrades > 0 ? totalRRSum / result.totalTrades : 0.0;
        result.avgTradePnl = result.totalTrades > 0 ? result.netPnl / result.totalTrades : 0.0;
        result.maxConsecutiveWins = maxWinStreak;
        result.longestLosingStreak = maxLossStreak;

        ddAnalysis.longestLosingStreak = maxLossStreak;
        ddAnalysis.peakValue = peakCapital;
        result.drawdownAnalysis = ddAnalysis;

        result.arabicSummary = generateArabicSummary(result);
        return result;
    }
}
