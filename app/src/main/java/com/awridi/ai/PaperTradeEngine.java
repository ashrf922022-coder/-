package com.awridi.ai;

import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PaperTradeEngine
 * Central manager for Paper Trading lifecycle (OPEN, MONITOR, CLOSE, CANCEL, REJECT).
 * Strictly guarantees LIVE_TRADING = DISABLED (PAPER / MOCK ONLY).
 */
public class PaperTradeEngine {

    public static final boolean LIVE_TRADING_ENABLED = false; // STRICTLY DISABLED
    public static final String SYMBOL_GOLD = MainActivity.GOLD_SYMBOL;

    private final PositionSizingEngine positionSizingEngine = new PositionSizingEngine();
    private final TradeMonitorEngine tradeMonitorEngine = new TradeMonitorEngine();
    private final RiskManagementEngine riskManagementEngine = new RiskManagementEngine();

    /**
     * Attempts to open a new paper trade from an AI Decision Result.
     * Enforces pipeline: AIDecision -> Risk Gate -> Trade Quality -> Validation -> Paper Trade.
     */
    public TradePosition openPaperTradeFromAIDecision(AIDecisionResult aiResult, SharedPreferences prefs) {
        if (aiResult == null) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.REJECTED;
            pos.rejectionReason = "نتيجة الذكاء الاصطناعي غير متوفرة (null AIDecisionResult).";
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "RISK_BLOCK", pos.rejectionReason, "REJECTED"));
            return pos;
        }

        // 1. Live Trading Guardrail
        if (LIVE_TRADING_ENABLED) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.REJECTED;
            pos.rejectionReason = "التداول الحقيقي حظر إجباري — LIVE TRADING IS DISABLED";
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "SECURITY_BLOCK", pos.rejectionReason, "REJECTED"));
            return pos;
        }

        // 2. Kill Switch Security Check
        if (KillSwitch.isActive(prefs)) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.reason = "مفتاح طوارئ الأمان (Kill Switch) مفعل حالياً.";
            pos.rejectionReason = pos.reason;
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "KILL_SWITCH_BLOCK", pos.reason, "BLOCKED"));
            return pos;
        }

        // 3. AIDecision Decision Check (WAIT -> NO TRADE)
        if (aiResult.decision == AIDecisionResult.Decision.WAIT) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.REJECTED;
            pos.decision = AIDecisionResult.Decision.WAIT;
            pos.reason = "القرار الذكي هو الانتظار (WAIT) — لا يتم فتح صفقة.";
            pos.rejectionReason = pos.reason;
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_REJECTED", pos.reason, "REJECTED"));
            return pos;
        }

        // 4. Risk Gate Approval Check
        if (!aiResult.riskApproved) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.decision = aiResult.decision;
            pos.reason = "مرفوضة بواسطة بوابة إدارة المخاطر (Risk Gate Block): " + aiResult.rejectionReason;
            pos.rejectionReason = aiResult.rejectionReason;
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "RISK_BLOCK", aiResult.rejectionReason, "BLOCKED"));
            return pos;
        }

        // 5. Setup Parameters & Price Boundary Validation
        double entry = aiResult.entryPrice > 0 ? aiResult.entryPrice : aiResult.currentPrice;
        double sl = aiResult.stopLoss;
        double tp = aiResult.takeProfit;

        TradePosition.Direction dir = aiResult.decision == AIDecisionResult.Decision.BUY ?
                TradePosition.Direction.BUY : TradePosition.Direction.SELL;

        // Validate SL / TP boundaries
        if (dir == TradePosition.Direction.BUY) {
            if (sl >= entry || tp <= entry) {
                TradePosition pos = new TradePosition();
                pos.status = TradePosition.Status.REJECTED;
                pos.rejectionReason = String.format(Locale.US,
                        "حدود الأسعار لصفقة الشراء غير صالحة: يجب أن يكون (SL %.2f < Entry %.2f < TP %.2f).", sl, entry, tp);
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "PRICE_VALIDATION_BLOCK", pos.rejectionReason, "REJECTED"));
                return pos;
            }
        } else if (dir == TradePosition.Direction.SELL) {
            if (tp >= entry || sl <= entry) {
                TradePosition pos = new TradePosition();
                pos.status = TradePosition.Status.REJECTED;
                pos.rejectionReason = String.format(Locale.US,
                        "حدود الأسعار لصفقة البيع غير صالحة: يجب أن يكون (TP %.2f < Entry %.2f < SL %.2f).", tp, entry, sl);
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "PRICE_VALIDATION_BLOCK", pos.rejectionReason, "REJECTED"));
                return pos;
            }
        }

        // 6. Account Balance & Limits Evaluation
        double capital = 10000.0;
        double riskPct = 1.0;
        double maxDailyLossPct = 3.0;
        if (prefs != null) {
            try {
                capital = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_CAPITAL, "10000"));
                riskPct = Double.parseDouble(prefs.getString(MainActivity.PREF_KEY_RISK_PCT, "1.0"));
                maxDailyLossPct = Double.parseDouble(prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0"));
            } catch (Exception ignored) {}
        }

        PortfolioManager.PortfolioSummary summary = PortfolioManager.calculateSummary(prefs);

        // Daily Loss Limit Check
        double maxDailyLossDollars = capital * (maxDailyLossPct / 100.0);
        if (summary.todayLossPnl >= maxDailyLossDollars) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.rejectionReason = "DAILY_LOSS_LIMIT: تم الوصول للحد الأقصى الخسارة اليومية.";
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "DAILY_LOSS_BLOCK", pos.rejectionReason, "BLOCKED"));
            return pos;
        }

        // Max Open Trades Check
        List<TradePosition> openTrades = TradeHistory.getOpenTrades(prefs);
        if (openTrades.size() >= summary.maxDailyTrades) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.rejectionReason = "MAX_OPEN_TRADES: تم الوصول للحد الأقصى لعدد الصفقات المفتوحة.";
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "MAX_OPEN_TRADES_BLOCK", pos.rejectionReason, "BLOCKED"));
            return pos;
        }

        // Consecutive Loss Check (e.g. 5 consecutive losses)
        if (summary.longestLosingStreak >= 5) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.rejectionReason = "MAX_CONSECUTIVE_LOSSES: تم حظر التداول بسبب 5 خسائر متتالية متتالية لحماية الرصيد.";
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "CONSECUTIVE_LOSS_BLOCK", pos.rejectionReason, "BLOCKED"));
            return pos;
        }

        // Calculate Position Sizing
        double totalOpenExposureLots = 0.0;
        for (TradePosition op : openTrades) {
            totalOpenExposureLots += op.positionSize;
        }

        PositionSizingEngine.SizingResult sizing = PositionSizingEngine.calculatePositionSize(
                capital,
                riskPct,
                entry,
                sl,
                dir,
                totalOpenExposureLots,
                openTrades.size(),
                2.0,
                10.0,
                summary.maxDailyTrades
        );

        if (!sizing.valid) {
            TradePosition pos = new TradePosition();
            pos.status = TradePosition.Status.BLOCKED;
            pos.rejectionReason = "خطأ في حجم الصفقة: " + sizing.rejectionReason;
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "POSITION_SIZING_BLOCK", pos.rejectionReason, "BLOCKED"));
            return pos;
        }

        // 7. Create & Persist Valid Paper Trade Position
        TradePosition pos = new TradePosition();
        pos.symbol = SYMBOL_GOLD;
        pos.direction = dir;
        pos.decision = aiResult.decision;
        pos.status = TradePosition.Status.OPEN;
        pos.entryPrice = entry;
        pos.stopLoss = sl;
        pos.takeProfit = tp;
        pos.positionSize = sizing.positionSizeLots;
        pos.riskAmount = sizing.riskAmountUsd;

        double riskDist = Math.abs(entry - sl);
        double rewardDist = Math.abs(tp - entry);
        pos.riskRewardRatio = riskDist > 0 ? rewardDist / riskDist : aiResult.riskRewardRatio;

        pos.currentPrice = entry;
        pos.openTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        pos.marketRegime = aiResult.marketRegime;
        pos.confluenceScore = aiResult.confluenceScore;
        pos.confidence = aiResult.confidence;
        pos.tradeQuality = aiResult.tradeQuality;
        pos.sourceDecisionId = aiResult.decisionId;
        pos.reason = "صفقة ورقية تم إنشاؤها عبر PaperTradeEngine ومحرك القرار الذكي.";

        TradeHistory.saveTrade(prefs, pos);

        TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_CREATED", "تم إنشاء الصفقة الورقية بنجاح", "SUCCESS"));
        TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_OPENED", "تم فتح الصفقة بسعر " + entry, "OPEN"));

        return pos;
    }

    /**
     * Monitors active trade against bar tick data.
     */
    public TradeMonitorEngine.MonitoringResult monitorTradePosition(TradePosition pos, MarketIntelligenceEngine.Bar bar, double accountBalance, SharedPreferences prefs) {
        TradeMonitorEngine.MonitoringResult res = tradeMonitorEngine.monitorBar(pos, bar, accountBalance);
        if (res.statusChanged && prefs != null) {
            TradeHistory.saveTrade(prefs, pos);

            if (res.conflictOccurred) {
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TP_SL_CONFLICT", res.auditLogMessage, pos.status.name()));
            }

            if (pos.status == TradePosition.Status.TP_HIT) {
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TP_HIT", "ضرب أخذ الربح", "WIN"));
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_CLOSED", "إغلاق الصفقة على ربح PnL: $" + pos.realizedPnL, "CLOSED"));
            } else if (pos.status == TradePosition.Status.SL_HIT) {
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "SL_HIT", "ضرب وقف الخسارة", "LOSS"));
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_CLOSED", "إغلاق الصفقة على خسارة PnL: $" + pos.realizedPnL, "CLOSED"));
            } else {
                TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_MONITORED", "مراقبة الصفقة - السعر الحالي: " + pos.currentPrice, pos.status.name()));
            }
        }
        return res;
    }

    /**
     * Manually close paper trade position.
     */
    public TradePosition closePaperTrade(String tradeId, double customExitPrice, SharedPreferences prefs) {
        TradePosition pos = TradeHistory.getTradeById(prefs, tradeId);
        if (pos == null) return null;

        if (pos.status == TradePosition.Status.OPEN || pos.status == TradePosition.Status.PENDING) {
            double exitPrice = customExitPrice > 0 ? customExitPrice : pos.currentPrice;
            pos.realizedPnL = PnLEngine.calculatePnL(pos.direction, pos.entryPrice, exitPrice, pos.positionSize);
            pos.status = TradePosition.Status.CLOSED;
            pos.closeTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            pos.unrealizedPnL = 0.0;
            pos.rMultiple = PnLEngine.calculateRMultiple(pos.realizedPnL, pos.riskAmount);

            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_CLOSED", "إغلاق الصفقة يدويًا بسعر " + exitPrice, "CLOSED"));
        }

        return pos;
    }

    /**
     * Cancel pending paper trade position.
     */
    public TradePosition cancelPaperTrade(String tradeId, SharedPreferences prefs) {
        TradePosition pos = TradeHistory.getTradeById(prefs, tradeId);
        if (pos == null) return null;

        if (pos.status == TradePosition.Status.PENDING || pos.status == TradePosition.Status.OPEN) {
            pos.status = TradePosition.Status.CANCELLED;
            pos.closeTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            TradeHistory.saveTrade(prefs, pos);
            TradeHistory.logAuditEvent(prefs, new TradeHistory.AuditEvent(pos.tradeId, "TRADE_CANCELLED", "إلغاء الصفقة", "CANCELLED"));
        }

        return pos;
    }
}
