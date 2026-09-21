package com.awridi.ai;

import android.content.SharedPreferences;

/**
 * Risk Intelligence Engine enforcing capital preservation rules, daily drawdown limits,
 * maximum risk per trade, and position sizing.
 */
public class RiskIntelligenceEngine {

    public static class RiskCheckResult {
        public final boolean isTradeAllowed;
        public final String statusArabicMessage;
        public final double allowedLotSize;

        public RiskCheckResult(boolean isTradeAllowed, String statusArabicMessage, double allowedLotSize) {
            this.isTradeAllowed = isTradeAllowed;
            this.statusArabicMessage = statusArabicMessage;
            this.allowedLotSize = allowedLotSize;
        }
    }

    public static RiskCheckResult evaluateRisk(double capital, double riskPctPerTrade, double maxDailyDrawdownPct,
                                                double currentDailyLossUsd, double entryPrice, double stopLossPrice) {
        if (capital <= 0) {
            return new RiskCheckResult(false, "رأس المال غير كافٍ لتنفيذ التداول.", 0);
        }

        double maxDailyLossAllowedUsd = capital * (maxDailyDrawdownPct / 100.0);
        if (currentDailyLossUsd >= maxDailyLossAllowedUsd) {
            return new RiskCheckResult(false, "تم تجاوز حد الخسارة اليومية الأقصى المسموح به (" + maxDailyDrawdownPct + "%). التداول متوقف لحماية الحساب.", 0);
        }

        double riskDistance = Math.abs(entryPrice - stopLossPrice);
        if (riskDistance <= 0.01) {
            return new RiskCheckResult(false, "مستوى وقف الخسارة قريب جداً من سعر الدخول مما يزيد خطورة الانزلاق السعري.", 0);
        }

        double maxTradeRiskUsd = capital * (riskPctPerTrade / 100.0);
        double calculatedLot = maxTradeRiskUsd / (riskDistance * 100.0);

        if (calculatedLot < 0.01) {
            return new RiskCheckResult(false, "حجم العقد الحسابي أقل من الحد الأدنى (0.01 لوت). يرجى زيادة رأس المال أو خفض مسافة الوقف.", 0);
        }

        return new RiskCheckResult(true, "تقييم المخاطر إيجابي وتوافق تام مع شروط إدارة رأس المال.", Math.min(10.0, calculatedLot));
    }
}
