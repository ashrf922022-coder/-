package com.awridi.ai;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Phase 10: Paper Trade Management, Smart Monitoring, and Complete Audit Verification Test Suite.
 * Fully verifies all 47 test cases including explicit mandatory numerical tests.
 */
public class Phase10PaperTradeManagementTest {

    private PaperTradeEngine paperTradeEngine;
    private PositionSizingEngine positionSizingEngine;
    private TradeMonitorEngine monitorEngine;
    private MockSharedPreferences mockPrefs;

    @Before
    public void setUp() {
        paperTradeEngine = new PaperTradeEngine();
        positionSizingEngine = new PositionSizingEngine();
        monitorEngine = new TradeMonitorEngine();
        mockPrefs = new MockSharedPreferences();

        mockPrefs.edit()
                .putString(MainActivity.PREF_KEY_CAPITAL, "10000.0")
                .putString(MainActivity.PREF_KEY_RISK_PCT, "1.0")
                .putString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0")
                .putString(PortfolioManager.PREF_KEY_MAX_DAILY_TRADES, "5")
                .apply();

        KillSwitch.deactivate(mockPrefs);
        TradeHistory.clearHistory(mockPrefs);
        TradeHistory.clearAuditTrail(mockPrefs);
    }

    private AIDecisionResult createValidBUYDecision() {
        AIDecisionResult res = new AIDecisionResult();
        res.decisionId = "DEC_BUY_1";
        res.decision = AIDecisionResult.Decision.BUY;
        res.symbol = "XAU/USD";
        res.currentPrice = 100.0;
        res.entryPrice = 100.0;
        res.stopLoss = 95.0;
        res.takeProfit = 105.0;
        res.riskRewardRatio = 1.0;
        res.positionSizeLot = 0.2;
        res.riskApproved = true;
        res.confidence = 85.0;
        res.confluenceScore = 80.0;
        res.tradeQuality = AIDecisionResult.TradeQuality.HIGH_QUALITY;
        res.marketRegime = MarketRegimeResult.Regime.TREND_UP;
        res.decisionReasons.add("Valid Uptrend and Strong Confluence");
        return res;
    }

    private AIDecisionResult createValidSELLDecision() {
        AIDecisionResult res = new AIDecisionResult();
        res.decisionId = "DEC_SELL_1";
        res.decision = AIDecisionResult.Decision.SELL;
        res.symbol = "XAU/USD";
        res.currentPrice = 100.0;
        res.entryPrice = 100.0;
        res.stopLoss = 105.0;
        res.takeProfit = 95.0;
        res.riskRewardRatio = 1.0;
        res.positionSizeLot = 0.2;
        res.riskApproved = true;
        res.confidence = 85.0;
        res.confluenceScore = 80.0;
        res.tradeQuality = AIDecisionResult.TradeQuality.HIGH_QUALITY;
        res.marketRegime = MarketRegimeResult.Regime.TREND_DOWN;
        res.decisionReasons.add("Valid Downtrend and Strong Confluence");
        return res;
    }

    // --- GROUP 1: TRADE CREATION (Tests 1-5) ---

    @Test
    public void test01_BUYCreation() {
        // Input: BUY decision with valid risk approval
        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: OPEN position, BUY direction
        assertNotNull(pos);
        assertEquals(TradePosition.Status.OPEN, pos.status);
        assertEquals(TradePosition.Direction.BUY, pos.direction);
        assertEquals(100.0, pos.entryPrice, 0.001);
    }

    @Test
    public void test02_SELLCreation() {
        // Input: SELL decision with valid risk approval
        AIDecisionResult aiRes = createValidSELLDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: OPEN position, SELL direction
        assertNotNull(pos);
        assertEquals(TradePosition.Status.OPEN, pos.status);
        assertEquals(TradePosition.Direction.SELL, pos.direction);
        assertEquals(100.0, pos.entryPrice, 0.001);
    }

    @Test
    public void test03_WAIT_NoTrade() {
        // Input: WAIT decision
        AIDecisionResult aiRes = createValidBUYDecision();
        aiRes.decision = AIDecisionResult.Decision.WAIT;

        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: Status REJECTED, no open trade
        assertEquals(TradePosition.Status.REJECTED, pos.status);
        assertTrue(TradeHistory.getOpenTrades(mockPrefs).isEmpty());
    }

    @Test
    public void test04_RiskGateBlock_NoTrade() {
        // Input: BUY decision but riskApproved = false
        AIDecisionResult aiRes = createValidBUYDecision();
        aiRes.riskApproved = false;
        aiRes.rejectionReason = "Max daily loss exceeded";

        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: Status BLOCKED
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.rejectionReason.contains("Max daily loss exceeded"));
        assertTrue(TradeHistory.getOpenTrades(mockPrefs).isEmpty());
    }

    @Test
    public void test05_KillSwitch_NoTrade() {
        // Input: Active Kill Switch
        KillSwitch.activate(mockPrefs);
        AIDecisionResult aiRes = createValidBUYDecision();

        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: Status BLOCKED
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.reason.contains("Kill Switch"));
        assertTrue(TradeHistory.getOpenTrades(mockPrefs).isEmpty());
    }

    // --- GROUP 2: PRICE VALIDATION (Tests 6-9) ---

    @Test
    public void test06_BUYValidSLTP() {
        // Input: BUY SL=95 < Entry=100 < TP=105
        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: OPEN position
        assertEquals(TradePosition.Status.OPEN, pos.status);
    }

    @Test
    public void test07_BUYInvalidSLTP() {
        // Input: BUY SL=105 >= Entry=100 (Invalid SL)
        AIDecisionResult aiRes = createValidBUYDecision();
        aiRes.stopLoss = 105.0; // Invalid for BUY

        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: REJECTED
        assertEquals(TradePosition.Status.REJECTED, pos.status);
        assertTrue(pos.rejectionReason.contains("غير صالحة"));
    }

    @Test
    public void test08_SELLValidSLTP() {
        // Input: SELL TP=95 < Entry=100 < SL=105
        AIDecisionResult aiRes = createValidSELLDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: OPEN position
        assertEquals(TradePosition.Status.OPEN, pos.status);
    }

    @Test
    public void test09_SELLInvalidSLTP() {
        // Input: SELL TP=105 >= Entry=100 (Invalid TP)
        AIDecisionResult aiRes = createValidSELLDecision();
        aiRes.takeProfit = 105.0; // Invalid for SELL

        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: REJECTED
        assertEquals(TradePosition.Status.REJECTED, pos.status);
        assertTrue(pos.rejectionReason.contains("غير صالحة"));
    }

    // --- GROUP 3: TP/SL MONITORING & NUMERICAL CONFLICT TESTS (Tests 10-17) ---

    @Test
    public void test10_BUY_TP_Numerical() {
        // Input: BUY Entry=100, SL=95, TP=105, High=106, Low=99
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.BUY;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 95.0;
        pos.takeProfit = 105.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 99.0, 105.0, "Bar1", false, false, 10000.0);

        // Expected Output: TP_HIT
        assertEquals(TradePosition.Status.TP_HIT, pos.status);
        assertEquals(105.0, pos.currentPrice, 0.001);
    }

    @Test
    public void test11_BUY_SL_Numerical() {
        // Input: BUY Entry=100, SL=95, TP=105, High=102, Low=94
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.BUY;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 95.0;
        pos.takeProfit = 105.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 102.0, 94.0, 95.0, "Bar1", false, false, 10000.0);

        // Expected Output: SL_HIT
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
        assertEquals(95.0, pos.currentPrice, 0.001);
    }

    @Test
    public void test12_SELL_TP_Numerical() {
        // Input: SELL Entry=100, SL=105, TP=95, High=101, Low=94
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.SELL;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 105.0;
        pos.takeProfit = 95.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 101.0, 94.0, 95.0, "Bar1", false, false, 10000.0);

        // Expected Output: TP_HIT
        assertEquals(TradePosition.Status.TP_HIT, pos.status);
        assertEquals(95.0, pos.currentPrice, 0.001);
    }

    @Test
    public void test13_SELL_SL_Numerical() {
        // Input: SELL Entry=100, SL=105, TP=95, High=106, Low=98
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.SELL;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 105.0;
        pos.takeProfit = 95.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 98.0, 105.0, "Bar1", false, false, 10000.0);

        // Expected Output: SL_HIT
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
        assertEquals(105.0, pos.currentPrice, 0.001);
    }

    @Test
    public void test14_BUY_Conflict_SameCandle() {
        // Input: BUY Entry=100, SL=95, TP=105, High=106, Low=94, No Intrabar Data
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.BUY;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 95.0;
        pos.takeProfit = 105.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 94.0, 100.0, "Bar1", false, false, 10000.0);

        // Expected Output: SL_HIT, conflict=true, resolution=CONSERVATIVE_SL_FIRST
        assertTrue(res.conflictOccurred);
        assertEquals("TP_SL_CONFLICT", pos.conflictType);
        assertEquals("CONSERVATIVE_SL_FIRST", pos.resolution);
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
    }

    @Test
    public void test15_SELL_Conflict_SameCandle() {
        // Input: SELL Entry=100, SL=105, TP=95, High=106, Low=94, No Intrabar Data
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.SELL;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 105.0;
        pos.takeProfit = 95.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 94.0, 100.0, "Bar1", false, false, 10000.0);

        // Expected Output: SL_HIT, conflict=true, resolution=CONSERVATIVE_SL_FIRST
        assertTrue(res.conflictOccurred);
        assertEquals("TP_SL_CONFLICT", pos.conflictType);
        assertEquals("CONSERVATIVE_SL_FIRST", pos.resolution);
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
    }

    @Test
    public void test16_Intrabar_TP_First() {
        // Input: Intrabar data present and isTpFirst = true
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.BUY;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 95.0;
        pos.takeProfit = 105.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 94.0, 100.0, "Bar1", true, true, 10000.0);

        // Expected Output: TP_HIT
        assertEquals(TradePosition.Status.TP_HIT, pos.status);
        assertEquals("REAL_INTRABAR_TP_FIRST", pos.resolution);
    }

    @Test
    public void test17_Intrabar_SL_First() {
        // Input: Intrabar data present and isTpFirst = false
        TradePosition pos = new TradePosition();
        pos.direction = TradePosition.Direction.BUY;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.stopLoss = 95.0;
        pos.takeProfit = 105.0;
        pos.positionSize = 1.0;

        TradeMonitorEngine.MonitoringResult res = monitorEngine.monitorBar(pos, 100.0, 106.0, 94.0, 100.0, "Bar1", true, false, 10000.0);

        // Expected Output: SL_HIT
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
        assertEquals("REAL_INTRABAR_SL_FIRST", pos.resolution);
    }

    // --- GROUP 4: POSITION SIZING (Tests 18-20) ---

    @Test
    public void test18_ValidPositionSize() {
        // Input: Capital=10000, Risk=1%, Entry=100, SL=95 (Risk distance $5)
        // 1 Lot XAU/USD = $100 per $1 move -> Risk $100 / ($5 * 100) = 0.20 lots
        PositionSizingEngine.SizingResult res = PositionSizingEngine.calculatePositionSize(
                10000.0, 1.0, 100.0, 95.0, TradePosition.Direction.BUY, 0.0, 0, 2.0, 10.0, 5
        );

        // Expected Output: Valid, lots = 0.20
        assertTrue(res.valid);
        assertEquals(0.20, res.positionSizeLots, 0.001);
        assertEquals(100.0, res.riskAmountUsd, 0.001);
    }

    @Test
    public void test19_InvalidPositionSize() {
        // Input: Negative Account Balance or NaN
        PositionSizingEngine.SizingResult res = PositionSizingEngine.calculatePositionSize(
                -500.0, 1.0, 100.0, 95.0, TradePosition.Direction.BUY, 0.0, 0, 2.0, 10.0, 5
        );

        // Expected Output: Invalid
        assertFalse(res.valid);
        assertTrue(res.rejectionReason.contains("غير صالح"));
    }

    @Test
    public void test20_RiskLimitProtection() {
        // Input: Requested risk 5% when max risk allowed is 2%
        PositionSizingEngine.SizingResult res = PositionSizingEngine.calculatePositionSize(
                10000.0, 5.0, 100.0, 95.0, TradePosition.Direction.BUY, 0.0, 0, 2.0, 10.0, 5
        );

        // Expected Output: Invalid, exceeds max risk limit
        assertFalse(res.valid);
        assertTrue(res.rejectionReason.contains("الحد الأقصى"));
    }

    // --- GROUP 5: P/L & R MULTIPLE NUMERICAL TESTS (Tests 21-25) ---

    @Test
    public void test21_BUYProfit_Numerical() {
        // Input: BUY Entry=100, Exit=110, Size=2 lots
        // P/L = (110 - 100) * 2 * 100 = +200 USD (scale factor 10 for test size 0.02 or direct lot math)
        // With size = 2 lots: P/L = (110 - 100) * 2 = $20 for unit multiplier or $2000 standard
        double pnl = PnLEngine.calculatePnL(TradePosition.Direction.BUY, 100.0, 110.0, 2.0);

        // Expected Output: P/L = +2000.0
        assertEquals(2000.0, pnl, 0.001);

        // Micro scale test for direct prompt matching (Size=0.02 lot = +20)
        double pnlDirect = PnLEngine.calculatePnL(TradePosition.Direction.BUY, 100.0, 110.0, 0.02);
        assertEquals(20.0, pnlDirect, 0.001);
    }

    @Test
    public void test22_BUYLoss_Numerical() {
        // Input: BUY Entry=100, Exit=95, Size=0.02 lot
        double pnl = PnLEngine.calculatePnL(TradePosition.Direction.BUY, 100.0, 95.0, 0.02);

        // Expected Output: P/L = -10.0
        assertEquals(-10.0, pnl, 0.001);
    }

    @Test
    public void test23_SELLProfit_Numerical() {
        // Input: SELL Entry=100, Exit=90, Size=0.02 lot
        double pnl = PnLEngine.calculatePnL(TradePosition.Direction.SELL, 100.0, 90.0, 0.02);

        // Expected Output: P/L = +20.0
        assertEquals(20.0, pnl, 0.001);
    }

    @Test
    public void test24_SELLLoss_Numerical() {
        // Input: SELL Entry=100, Exit=105, Size=0.02 lot
        double pnl = PnLEngine.calculatePnL(TradePosition.Direction.SELL, 100.0, 105.0, 0.02);

        // Expected Output: P/L = -10.0
        assertEquals(-10.0, pnl, 0.001);
    }

    @Test
    public void test25_RMultiple_Numerical() {
        // Case 1: Risk = 10, Profit = 20 -> R = +2.0
        double r1 = PnLEngine.calculateRMultiple(20.0, 10.0);
        assertEquals(2.0, r1, 0.001);

        // Case 2: Risk = 10, Loss = -10 -> R = -1.0
        double r2 = PnLEngine.calculateRMultiple(-10.0, 10.0);
        assertEquals(-1.0, r2, 0.001);
    }

    // --- GROUP 6: RISK LIMIT TESTS (Tests 26-30) ---

    @Test
    public void test26_DailyLossLimit_Numerical() {
        // Input: Daily Loss Limit = $100, Current Daily Loss = $100, New Trade Risk = $20
        PortfolioManager.PortfolioTrade lossT = new PortfolioManager.PortfolioTrade();
        lossT.id = "LOSS_TODAY";
        lossT.pnl = -300.0; // 3% of 10000 = 300 (Daily loss limit reached)
        lossT.status = "LOSS";
        lossT.date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        PortfolioManager.saveTrades(mockPrefs, java.util.Collections.singletonList(lossT));

        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: TRADE BLOCKED, Reason contains DAILY_LOSS_LIMIT
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.rejectionReason.contains("DAILY_LOSS_LIMIT"));
    }

    @Test
    public void test27_MaxOpenTrades_Numerical() {
        // Input: Max Open Trades = 3, Current Open Trades = 3
        mockPrefs.edit().putString(PortfolioManager.PREF_KEY_MAX_DAILY_TRADES, "3").apply();

        for (int i = 1; i <= 3; i++) {
            TradePosition openPos = new TradePosition();
            openPos.tradeId = "OPEN_" + i;
            openPos.status = TradePosition.Status.OPEN;
            TradeHistory.saveTrade(mockPrefs, openPos);
        }

        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: TRADE BLOCKED, Reason contains MAX_OPEN_TRADES
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.rejectionReason.contains("MAX_OPEN_TRADES"));
    }

    @Test
    public void test28_MaxExposure() {
        // Input: Open trades already total 10.0 lots exposure
        for (int i = 1; i <= 2; i++) {
            TradePosition openPos = new TradePosition();
            openPos.tradeId = "EXP_" + i;
            openPos.status = TradePosition.Status.OPEN;
            openPos.positionSize = 5.0; // Total 10 lots
            TradeHistory.saveTrade(mockPrefs, openPos);
        }

        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: BLOCKED due to exposure limits
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
    }

    @Test
    public void test29_ConsecutiveLossBlock() {
        // Input: 5 consecutive losses
        List<PortfolioManager.PortfolioTrade> trades = new ArrayList<>();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        for (int i = 1; i <= 5; i++) {
            PortfolioManager.PortfolioTrade t = new PortfolioManager.PortfolioTrade();
            t.id = "LOSS_" + i;
            t.pnl = -10.0;
            t.status = "LOSS";
            t.date = today;
            trades.add(t);
        }
        PortfolioManager.saveTrades(mockPrefs, trades);

        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: BLOCKED due to consecutive losses
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.rejectionReason.contains("CONSECUTIVE_LOSSES"));
    }

    @Test
    public void test30_KillSwitch_Block() {
        // Input: Kill Switch Active
        KillSwitch.activate(mockPrefs);

        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        // Expected Output: BLOCKED
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
    }

    // --- GROUP 7: TRADE HISTORY OPERATIONS (Tests 31-35) ---

    @Test
    public void test31_SaveTrade() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TEST_HIST_1";
        pos.status = TradePosition.Status.OPEN;

        TradeHistory.saveTrade(mockPrefs, pos);

        assertNotNull(TradeHistory.getTradeById(mockPrefs, "TEST_HIST_1"));
    }

    @Test
    public void test32_RetrieveTrade() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TEST_HIST_2";
        pos.entryPrice = 2650.0;

        TradeHistory.saveTrade(mockPrefs, pos);

        TradePosition retrieved = TradeHistory.getTradeById(mockPrefs, "TEST_HIST_2");
        assertNotNull(retrieved);
        assertEquals(2650.0, retrieved.entryPrice, 0.001);
    }

    @Test
    public void test33_CloseTrade() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TEST_CLOSE_1";
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.currentPrice = 110.0;
        pos.positionSize = 1.0;
        TradeHistory.saveTrade(mockPrefs, pos);

        TradePosition closed = paperTradeEngine.closePaperTrade("TEST_CLOSE_1", 110.0, mockPrefs);

        assertNotNull(closed);
        assertEquals(TradePosition.Status.CLOSED, closed.status);
        assertEquals(1000.0, closed.realizedPnL, 0.001); // (110 - 100) * 1.0 * 100
    }

    @Test
    public void test34_DeleteTrade() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TEST_DEL_1";
        TradeHistory.saveTrade(mockPrefs, pos);

        boolean deleted = TradeHistory.deleteTradeById(mockPrefs, "TEST_DEL_1");

        assertTrue(deleted);
        assertNull(TradeHistory.getTradeById(mockPrefs, "TEST_DEL_1"));
    }

    @Test
    public void test35_ClearHistory() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TEST_CLEAR_1";
        TradeHistory.saveTrade(mockPrefs, pos);

        TradeHistory.clearHistory(mockPrefs);

        assertTrue(TradeHistory.loadPositions(mockPrefs).isEmpty());
    }

    // --- GROUP 8: AUDIT TRAIL LOGGING (Tests 36-42) ---

    @Test
    public void test36_Audit_TRADE_CREATED() {
        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("TRADE_CREATED")));
    }

    @Test
    public void test37_Audit_TRADE_OPENED() {
        AIDecisionResult aiRes = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("TRADE_OPENED")));
    }

    @Test
    public void test38_Audit_TP_HIT() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "AUDIT_TP_1";
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.takeProfit = 105.0;
        pos.stopLoss = 95.0;

        MarketIntelligenceEngine.Bar bar = new MarketIntelligenceEngine.Bar(100, 106, 99, 105, 1000);
        paperTradeEngine.monitorTradePosition(pos, bar, 10000.0, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("TP_HIT")));
    }

    @Test
    public void test39_Audit_SL_HIT() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "AUDIT_SL_1";
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.takeProfit = 105.0;
        pos.stopLoss = 95.0;

        MarketIntelligenceEngine.Bar bar = new MarketIntelligenceEngine.Bar(100, 101, 94, 95, 1000);
        paperTradeEngine.monitorTradePosition(pos, bar, 10000.0, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("SL_HIT")));
    }

    @Test
    public void test40_Audit_TP_SL_CONFLICT() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "AUDIT_CONF_1";
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = 100.0;
        pos.takeProfit = 105.0;
        pos.stopLoss = 95.0;

        MarketIntelligenceEngine.Bar bar = new MarketIntelligenceEngine.Bar(100, 106, 94, 100, 1000);
        paperTradeEngine.monitorTradePosition(pos, bar, 10000.0, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("TP_SL_CONFLICT")));
    }

    @Test
    public void test41_Audit_RISK_BLOCK() {
        AIDecisionResult aiRes = createValidBUYDecision();
        aiRes.riskApproved = false;
        aiRes.rejectionReason = "Risk Gate Rejected";

        paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("RISK_BLOCK")));
    }

    @Test
    public void test42_Audit_KILL_SWITCH_BLOCK() {
        KillSwitch.activate(mockPrefs);
        AIDecisionResult aiRes = createValidBUYDecision();

        paperTradeEngine.openPaperTradeFromAIDecision(aiRes, mockPrefs);

        List<TradeHistory.AuditEvent> trail = TradeHistory.loadAuditTrail(mockPrefs);
        assertTrue(trail.stream().anyMatch(e -> e.action.equals("KILL_SWITCH_BLOCK")));
    }

    // --- GROUP 9: LOOK-AHEAD BIAS PROTECTION (Test 43) ---

    @Test
    public void test43_LookAheadBiasProtection_Numerical() {
        // Evaluate Candle N (index 40) on a base sequence
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double p = 2500.0;
        for (int i = 0; i < 50; i++) {
            p += (i % 2 == 0) ? 1.0 : -0.5;
            bars.add(new MarketIntelligenceEngine.Bar(p - 1, p + 1, p - 1.5, p, 1000));
        }

        AIDecisionEngine aiEngine = new AIDecisionEngine();
        AIDecisionResult resBefore = aiEngine.evaluateAtCandle(bars, 40, "XAU/USD", "15min", mockPrefs);

        // Add extreme huge movement in candle 41 (N+1)
        bars.set(41, new MarketIntelligenceEngine.Bar(3000.0, 3500.0, 2000.0, 3400.0, 50000));

        // Re-evaluate at Candle N (index 40)
        AIDecisionResult resAfter = aiEngine.evaluateAtCandle(bars, 40, "XAU/USD", "15min", mockPrefs);

        // Expected Output: Result at Candle N is 100% identical regardless of Candle N+1
        assertEquals(resBefore.decision, resAfter.decision);
        assertEquals(resBefore.currentPrice, resAfter.currentPrice, 0.0001);
        assertEquals(resBefore.confidence, resAfter.confidence, 0.0001);
        assertEquals(resBefore.confluenceScore, resAfter.confluenceScore, 0.0001);
    }

    // --- GROUP 10: DETERMINISM TEST (Test 44) ---

    @Test
    public void test44_Determinism_Test() {
        // Run identical backtest twice on same dataset
        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        double p = 2500.0;
        for (int i = 0; i < 100; i++) {
            p += (i % 2 == 0) ? 2.0 : -1.0;
            bars.add(new MarketIntelligenceEngine.Bar(p - 1, p + 1.5, p - 1.5, p, 1500));
        }

        BacktestEngine.BacktestParams params = new BacktestEngine.BacktestParams();
        params.symbol = "XAU/USD";
        params.initialCapital = 10000.0;

        BacktestEngine.BacktestResult run1 = BacktestEngine.runPaperTradeBacktest(bars, params, mockPrefs);
        BacktestEngine.BacktestResult run2 = BacktestEngine.runPaperTradeBacktest(bars, params, mockPrefs);

        // Expected Output: Exact identical values across all key metrics
        assertEquals(run1.totalTrades, run2.totalTrades);
        assertEquals(run1.winningTrades, run2.winningTrades);
        assertEquals(run1.losingTrades, run2.losingTrades);
        assertEquals(run1.netPnl, run2.netPnl, 0.00001);
        assertEquals(run1.winRate, run2.winRate, 0.00001);
        assertEquals(run1.profitFactor, run2.profitFactor, 0.00001);
        assertEquals(run1.maxDrawdownPct, run2.maxDrawdownPct, 0.00001);
        assertEquals(run1.avgRiskReward, run2.avgRiskReward, 0.00001);
    }

    // --- GROUP 11: SECURITY & LIVE TRADING PROTECTION (Tests 45-47) ---

    @Test
    public void test45_LiveTradingDisabled() {
        // Assert LIVE_TRADING_ENABLED is strictly false across all execution engines
        assertFalse(PaperTradeEngine.LIVE_TRADING_ENABLED);
        assertFalse(ExecutionEngine.LIVE_TRADING_ENABLED);
    }

    @Test
    public void test46_NoBrokerExecution() {
        // Attempting to execute live order returns rejected/failed
        ExecutionOrder order = new ExecutionOrder();
        order.tradingMode = ExecutionOrder.TradingMode.LIVE_TRADING;

        ExecutionEngine engine = new ExecutionEngine();
        ExecutionOrder res = engine.executeOrder(order, 10000.0, 0.0, mockPrefs);

        assertEquals(ExecutionOrder.OrderStatus.REJECTED, res.status);
        assertTrue(res.rejectionReason.contains("LIVE TRADING IS DISABLED"));
    }

    @Test
    public void test47_MockDataLabeled() {
        // Verify mock data batch contains MOCK label indicator
        HistoricalDataProvider.HistoricalDataBatch batch = HistoricalDataProvider.generateMockBars("XAU/USD", "15min", 50, 2650.0, 123);
        assertNotNull(batch);
        assertTrue(batch.isMock);
        assertTrue(batch.dataLabel.contains("MOCK DATA"));
    }

    @Test
    public void test48_UnifiedDataSource_NoDuplicates() {
        AIDecisionResult decision = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(decision, mockPrefs);
        assertNotNull(pos);
        assertEquals(TradePosition.Status.OPEN, pos.status);

        List<TradePosition> positions = TradeHistory.loadPositions(mockPrefs);
        assertEquals(1, positions.size());

        List<PortfolioManager.PortfolioTrade> pTrades = PortfolioManager.loadTrades(mockPrefs);
        assertEquals(1, pTrades.size());
        assertEquals(pos.tradeId, pTrades.get(0).id);

        List<PaperTradingManager.PaperTrade> ptList = PaperTradingManager.loadPaperTrades(mockPrefs);
        assertEquals(1, ptList.size());
        assertEquals(pos.tradeId, ptList.get(0).id);
    }

    @Test
    public void test49_TradeClosing_StatusAndPnL_Transition() {
        AIDecisionResult decision = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(decision, mockPrefs);
        assertNotNull(pos);

        // Close trade manually at profit target price $2610
        TradePosition closed = paperTradeEngine.closePaperTrade(pos.tradeId, 2610.0, mockPrefs);
        assertNotNull(closed);
        assertEquals(TradePosition.Status.CLOSED, closed.status);
        assertTrue(closed.realizedPnL > 0);
        assertEquals(0.0, closed.unrealizedPnL, 0.001);

        PortfolioManager.PortfolioSummary sum = PortfolioManager.calculateSummary(mockPrefs);
        assertEquals(1, sum.totalTrades);
        assertEquals(1, sum.winningTrades);
        assertTrue(sum.totalPnl > 0);
    }

    @Test
    public void test50_MonitorConstructorInjection() {
        TradeMonitorEngine customMonitor = new TradeMonitorEngine();
        PaperTradeEngine engineWithInjection = new PaperTradeEngine(customMonitor);
        assertNotNull(engineWithInjection);

        AIDecisionResult decision = createValidBUYDecision();
        TradePosition pos = engineWithInjection.openPaperTradeFromAIDecision(decision, mockPrefs);
        assertNotNull(pos);
        assertEquals(TradePosition.Status.OPEN, pos.status);
    }

    @Test
    public void test51_PositionSizing_ExplicitNonPositiveLimits() {
        // Test maxRiskPercentage <= 0
        PositionSizingEngine.SizingResult resRisk = PositionSizingEngine.calculatePositionSize(
                10000.0, 1.0, 2600.0, 2590.0, TradePosition.Direction.BUY, 0.0, 0, 0.0, 10.0, 5);
        assertFalse(resRisk.valid);
        assertTrue(resRisk.rejectionReason.contains("Max Risk % <= 0"));

        // Test maxExposureLots <= 0
        PositionSizingEngine.SizingResult resExp = PositionSizingEngine.calculatePositionSize(
                10000.0, 1.0, 2600.0, 2590.0, TradePosition.Direction.BUY, 0.0, 0, 2.0, 0.0, 5);
        assertFalse(resExp.valid);
        assertTrue(resExp.rejectionReason.contains("Max Exposure Lots <= 0"));

        // Test maxOpenTrades <= 0
        PositionSizingEngine.SizingResult resOpen = PositionSizingEngine.calculatePositionSize(
                10000.0, 1.0, 2600.0, 2590.0, TradePosition.Direction.BUY, 0.0, 0, 2.0, 10.0, 0);
        assertFalse(resOpen.valid);
        assertTrue(resOpen.rejectionReason.contains("Max Open Trades <= 0"));
    }

    @Test
    public void test52_ExecutionEngine_SyncsWithTradeHistory() {
        ExecutionEngine execEngine = new ExecutionEngine();
        ExecutionOrder order = new ExecutionOrder();
        order.action = ExecutionOrder.Action.BUY;
        order.orderType = ExecutionOrder.OrderType.MARKET;
        order.price = 2600.0;
        order.stopLoss = 2590.0;
        order.takeProfit = 2620.0;
        order.lotSize = 0.1;
        order.signalSource = "TestExecution";

        ExecutionOrder processed = execEngine.executeOrder(order, 10000.0, 0.0, mockPrefs);
        assertNotNull(processed);
        assertEquals(ExecutionOrder.OrderStatus.FILLED, processed.status);

        List<TradePosition> positions = TradeHistory.loadPositions(mockPrefs);
        assertFalse(positions.isEmpty());
        assertEquals(TradePosition.Status.OPEN, positions.get(0).status);
    }

    @Test
    public void test53_LiveTradingDisabledAssertion() {
        assertFalse(PaperTradeEngine.LIVE_TRADING_ENABLED);
        ExecutionEngine execEngine = new ExecutionEngine();
        ExecutionOrder liveOrder = new ExecutionOrder();
        liveOrder.tradingMode = ExecutionOrder.TradingMode.LIVE_TRADING;
        liveOrder.action = ExecutionOrder.Action.BUY;
        liveOrder.price = 2600.0;

        ExecutionOrder processed = execEngine.executeOrder(liveOrder, 10000.0, 0.0, mockPrefs);
        assertEquals(ExecutionOrder.OrderStatus.REJECTED, processed.status);
        assertTrue(processed.rejectionReason.contains("التداول الحقيقي غير مفعل"));
    }

    @Test
    public void test54_TP_SL_Conflict_Preserved() {
        TradePosition pos = new TradePosition();
        pos.tradeId = "TP_CONFLICT_TEST";
        pos.direction = TradePosition.Direction.BUY;
        pos.entryPrice = 2600.0;
        pos.stopLoss = 2590.0;
        pos.takeProfit = 2610.0;
        pos.positionSize = 0.1;
        pos.status = TradePosition.Status.OPEN;

        // Bar touches High = 2615 (>= TP) and Low = 2585 (<= SL)
        MarketIntelligenceEngine.Bar bar = new MarketIntelligenceEngine.Bar(2600.0, 2615.0, 2585.0, 2595.0, 100.0);

        TradeMonitorEngine.MonitoringResult monRes = monitorEngine.monitorBar(pos, bar, 10000.0);
        assertTrue(monRes.statusChanged);
        assertTrue(monRes.conflictOccurred);
        assertEquals("CONSERVATIVE_SL_FIRST", pos.resolution);
        assertEquals(TradePosition.Status.SL_HIT, pos.status);
    }

    @Test
    public void test55_KillSwitchAndRiskGate_BlockTrade() {
        KillSwitch.activate(mockPrefs);
        AIDecisionResult decision = createValidBUYDecision();
        TradePosition pos = paperTradeEngine.openPaperTradeFromAIDecision(decision, mockPrefs);

        assertNotNull(pos);
        assertEquals(TradePosition.Status.BLOCKED, pos.status);
        assertTrue(pos.rejectionReason.contains("Kill Switch"));
    }

    @Test
    public void test56_BacktestFrictionDeductedFromPnL() {
        List<MarketIntelligenceEngine.Bar> bars = HistoricalDataProvider.generateMockBars("XAU/USD", "15min", 30, 2600.0, 42).bars;

        // Case A: Zero friction (0 commission, 0 spread, 0 slippage)
        BacktestEngine.BacktestParams paramsA = new BacktestEngine.BacktestParams();
        paramsA.commissionPerLot = 0.0;
        paramsA.spreadPips = 0.0;
        paramsA.slippagePips = 0.0;
        paramsA.customPositionSizeLot = 1.0;

        BacktestEngine.BacktestResult resA = BacktestEngine.runAIBacktest(bars, paramsA, mockPrefs);

        // Case B: Positive friction (Commission + Spread + Slippage)
        BacktestEngine.BacktestParams paramsB = new BacktestEngine.BacktestParams();
        paramsB.commissionPerLot = 7.0;
        paramsB.spreadPips = 2.0;
        paramsB.slippagePips = 1.0;
        paramsB.customPositionSizeLot = 1.0;

        BacktestEngine.BacktestResult resB = BacktestEngine.runAIBacktest(bars, paramsB, mockPrefs);

        assertEquals(resA.totalTrades, resB.totalTrades);
        if (resA.totalTrades > 0) {
            assertTrue("PnL with friction must be strictly less than PnL without friction", resB.netPnl < resA.netPnl);
            for (int k = 0; k < resA.trades.size(); k++) {
                BacktestEngine.BacktestTrade tA = resA.trades.get(k);
                BacktestEngine.BacktestTrade tB = resB.trades.get(k);
                double totalFrictionCostB = tB.commissionPaidUsd + tB.spreadSlippageCostUsd;
                assertTrue(totalFrictionCostB > 0);
                assertTrue(tB.pnlUsd < tA.pnlUsd);
            }
        }
    }

    @Test
    public void test57_BacktestIsolationFromPaperTradeHistory() {
        // Clear paper trade history
        TradeHistory.clearHistory(mockPrefs);
        assertEquals(0, TradeHistory.loadPositions(mockPrefs).size());

        List<MarketIntelligenceEngine.Bar> bars = new ArrayList<>();
        bars.add(new MarketIntelligenceEngine.Bar(2600.0, 2605.0, 2595.0, 2600.0, 100.0));
        bars.add(new MarketIntelligenceEngine.Bar(2600.0, 2620.0, 2595.0, 2615.0, 100.0));

        BacktestEngine.BacktestParams params = new BacktestEngine.BacktestParams();
        BacktestEngine.BacktestResult res = BacktestEngine.runAIBacktest(bars, params, mockPrefs);

        assertNotNull(res);
        // Assert that running a backtest does NOT create paper trade records in TradeHistory
        assertEquals(0, TradeHistory.loadPositions(mockPrefs).size());
    }

    // --- MOCK SHAREDPREFERENCES UTILITY FOR JUNIT RUNTIME ---
    private static class MockSharedPreferences implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String k, String def) { return map.containsKey(k) ? (String) map.get(k) : def; }
        @Override public Set<String> getStringSet(String k, Set<String> def) { return (Set<String>) map.getOrDefault(k, def); }
        @Override public int getInt(String k, int def) { return map.containsKey(k) ? (Integer) map.get(k) : def; }
        @Override public long getLong(String k, long def) { return map.containsKey(k) ? (Long) map.get(k) : def; }
        @Override public float getFloat(String k, float def) { return map.containsKey(k) ? (Float) map.get(k) : def; }
        @Override public boolean getBoolean(String k, boolean def) { return map.containsKey(k) ? (Boolean) map.get(k) : def; }
        @Override public boolean contains(String k) { return map.containsKey(k); }
        @Override public Editor edit() { return new MockEditor(map); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

        private static class MockEditor implements Editor {
            private final Map<String, Object> map;
            MockEditor(Map<String, Object> map) { this.map = map; }
            @Override public Editor putString(String k, String v) { map.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { map.put(k, v); return this; }
            @Override public Editor putInt(String k, int v) { map.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { map.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { map.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { map.put(k, v); return this; }
            @Override public Editor remove(String k) { map.remove(k); return this; }
            @Override public Editor clear() { map.clear(); return this; }
            @Override public boolean commit() { return true; }
            @Override public void apply() {}
        }
    }
}
