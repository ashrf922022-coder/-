package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SignalConfluenceEngine {

    public static class ConfluenceResult {
        public double confluenceScore = 0.0; // 0 to 100
        public String confluenceLevel = "LOW"; // "HIGH", "MEDIUM", "LOW"
        public int supportingCount = 0;
        public int conflictingCount = 0;
        public int neutralCount = 0;

        public List<String> supportingSignals = new ArrayList<>();
        public List<String> conflictingSignals = new ArrayList<>();
        public List<String> neutralSignals = new ArrayList<>();

        public String overallDirection = "NEUTRAL"; // "BULLISH", "BEARISH", "NEUTRAL"
        public String summaryArabic = "";
    }

    public ConfluenceResult evaluateConfluence(
            TradingDecisionResult tdResult,
            SignalEngine.SignalResult signalEngineResult,
            TradeSetup tradeSetup,
            RiskManagementEngine.RiskResult riskResult) {

        ConfluenceResult result = new ConfluenceResult();

        if (tdResult == null && signalEngineResult == null) {
            result.confluenceScore = 0.0;
            result.confluenceLevel = "LOW";
            result.neutralCount = 1;
            result.neutralSignals.add("لا توجد بيانات إشارات كافية لتحليل التوافق");
            result.summaryArabic = "التوافق منخفض جداً بسبب غياب بيانات التحليل الفني.";
            return result;
        }

        // Determine target direction from signal engine or setup or decision
        String targetDir = "NEUTRAL";
        if (signalEngineResult != null && signalEngineResult.signalType != SignalEngine.SignalType.HOLD) {
            targetDir = signalEngineResult.signalType == SignalEngine.SignalType.BUY ? "BULLISH" : "BEARISH";
        } else if (tradeSetup != null && tradeSetup.valid) {
            targetDir = tradeSetup.direction == TradeSetup.Direction.BUY ? "BULLISH" : "BEARISH";
        } else if (tdResult != null) {
            if (tdResult.direction == TradingDecisionResult.Direction.BULLISH || tdResult.decision == TradingDecisionResult.Decision.BUY) {
                targetDir = "BULLISH";
            } else if (tdResult.direction == TradingDecisionResult.Direction.BEARISH || tdResult.decision == TradingDecisionResult.Decision.SELL) {
                targetDir = "BEARISH";
            }
        }

        result.overallDirection = targetDir;

        int totalWeight = 0;
        int alignedScore = 0;

        // 1. Trend Factor (Weight: 20)
        totalWeight += 20;
        if (tdResult != null) {
            boolean isTrendBullish = tdResult.trend.contains("صاعد");
            boolean isTrendBearish = tdResult.trend.contains("هابط");

            if ("BULLISH".equals(targetDir)) {
                if (isTrendBullish) {
                    alignedScore += 20;
                    result.supportingCount++;
                    result.supportingSignals.add("الاتجاه الفني العام صاعد ويتماشى مع الصفقة (" + tdResult.trend + ")");
                } else if (isTrendBearish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("الاتجاه الفني العام هابط ويتعارض مع إشارة الشراء (" + tdResult.trend + ")");
                } else {
                    alignedScore += 10;
                    result.neutralCount++;
                    result.neutralSignals.add("الاتجاه الفني حيادي / عرضي (" + tdResult.trend + ")");
                }
            } else if ("BEARISH".equals(targetDir)) {
                if (isTrendBearish) {
                    alignedScore += 20;
                    result.supportingCount++;
                    result.supportingSignals.add("الاتجاه الفني العام هابط ويتماشى مع الصفقة (" + tdResult.trend + ")");
                } else if (isTrendBullish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("الاتجاه الفني العام صاعد ويتعارض مع إشارة البيع (" + tdResult.trend + ")");
                } else {
                    alignedScore += 10;
                    result.neutralCount++;
                    result.neutralSignals.add("الاتجاه الفني حيادي / عرضي (" + tdResult.trend + ")");
                }
            } else {
                alignedScore += 10;
                result.neutralCount++;
                result.neutralSignals.add("الاتجاه الفني العام: " + tdResult.trend);
            }
        }

        // 2. Momentum Factor (Weight: 20)
        totalWeight += 20;
        if (tdResult != null) {
            boolean momBullish = tdResult.momentum.contains("صاعد") || tdResult.rsi >= 52;
            boolean momBearish = tdResult.momentum.contains("هابط") || tdResult.rsi <= 48;

            if ("BULLISH".equals(targetDir)) {
                if (momBullish && !tdResult.momentum.contains("Overbought")) {
                    alignedScore += 20;
                    result.supportingCount++;
                    result.supportingSignals.add("الزخم إيجابي صاعد (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                } else if (momBearish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("الزخم سلبي هابط يتعارض مع الشراء (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                } else {
                    alignedScore += 10;
                    result.neutralCount++;
                    result.neutralSignals.add("الزخم متوازن / تشبع محتمل (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                }
            } else if ("BEARISH".equals(targetDir)) {
                if (momBearish && !tdResult.momentum.contains("Oversold")) {
                    alignedScore += 20;
                    result.supportingCount++;
                    result.supportingSignals.add("الزخم سلبي هابط (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                } else if (momBullish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("الزخم إيجابي صاعد يتعارض مع البيع (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                } else {
                    alignedScore += 10;
                    result.neutralCount++;
                    result.neutralSignals.add("الزخم متوازن / تشبع محتمل (RSI = " + String.format(Locale.US, "%.1f", tdResult.rsi) + ")");
                }
            } else {
                alignedScore += 10;
                result.neutralCount++;
                result.neutralSignals.add("مؤشر الزخم RSI: " + String.format(Locale.US, "%.1f", tdResult.rsi));
            }
        }

        // 3. Price Structure & Support/Resistance Factor (Weight: 15)
        totalWeight += 15;
        if (tdResult != null) {
            String struct = tdResult.priceStructure;
            boolean structBullish = struct.contains("صعودي") || struct.contains("Higher High") || struct.contains("رفض هبوطي");
            boolean structBearish = struct.contains("هبوطي") || struct.contains("Lower High") || struct.contains("رفض صعودي");

            if ("BULLISH".equals(targetDir)) {
                if (structBullish) {
                    alignedScore += 15;
                    result.supportingCount++;
                    result.supportingSignals.add("بنية السعر تدعم الشراء: " + struct);
                } else if (structBearish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("بنية السعر سلبية وتتعارض مع الشراء: " + struct);
                } else {
                    alignedScore += 7;
                    result.neutralCount++;
                    result.neutralSignals.add("بنية السعر محايدة داخل النطاق: " + struct);
                }
            } else if ("BEARISH".equals(targetDir)) {
                if (structBearish) {
                    alignedScore += 15;
                    result.supportingCount++;
                    result.supportingSignals.add("بنية السعر تدعم البيع: " + struct);
                } else if (structBullish) {
                    result.conflictingCount++;
                    result.conflictingSignals.add("بنية السعر إيجابية وتتعارض مع البيع: " + struct);
                } else {
                    alignedScore += 7;
                    result.neutralCount++;
                    result.neutralSignals.add("بنية السعر محايدة داخل النطاق: " + struct);
                }
            } else {
                alignedScore += 7;
                result.neutralCount++;
                result.neutralSignals.add("بنية السعر: " + struct);
            }
        }

        // 4. Signal Engine Signal (Weight: 15)
        totalWeight += 15;
        if (signalEngineResult != null) {
            if (signalEngineResult.signalType != SignalEngine.SignalType.HOLD) {
                boolean sigBull = signalEngineResult.signalType == SignalEngine.SignalType.BUY;
                if (("BULLISH".equals(targetDir) && sigBull) || ("BEARISH".equals(targetDir) && !sigBull)) {
                    alignedScore += 15;
                    result.supportingCount++;
                    result.supportingSignals.add("محرك الإشارات (Signal Engine) يعطي إشارة " + signalEngineResult.signalType.name() + " بنسبة ثقة " + String.format(Locale.US, "%.0f%%", signalEngineResult.confidence));
                } else {
                    result.conflictingCount++;
                    result.conflictingSignals.add("إشارة محرك الإشارات تتعارض مع الاتجاه المطلوب");
                }
            } else {
                alignedScore += 5;
                result.neutralCount++;
                result.neutralSignals.add("محرك الإشارات يعطي HOLD (انتظار)");
            }
        }

        // 5. Trade Setup Quality & RR (Weight: 15)
        totalWeight += 15;
        if (tradeSetup != null) {
            if (tradeSetup.valid && tradeSetup.riskRewardRatio >= 1.5) {
                alignedScore += 15;
                result.supportingCount++;
                result.supportingSignals.add("إعداد الصفقة (Trade Setup) صالح بنسبة مخاطرة/عائد 1:" + String.format(Locale.US, "%.2f", tradeSetup.riskRewardRatio));
            } else if (!tradeSetup.valid) {
                result.conflictingCount++;
                result.conflictingSignals.add("إعداد الصفقة غير صالح أو جودة الدخول ضعيفة");
            } else {
                alignedScore += 8;
                result.neutralCount++;
                result.neutralSignals.add("نسبة المخاطرة/العائد أقل من Target Ideal (R:R = 1:" + String.format(Locale.US, "%.2f", tradeSetup.riskRewardRatio) + ")");
            }
        }

        // 6. Risk Management Approval (Weight: 15)
        totalWeight += 15;
        if (riskResult != null) {
            if (riskResult.valid) {
                alignedScore += 15;
                result.supportingCount++;
                result.supportingSignals.add("إدارة المخاطر (Risk Management): الصفقة مقبولة وتطابق حدود الحساب");
            } else {
                result.conflictingCount++;
                result.conflictingSignals.add("إدارة المخاطر رفعت اعتراضاً: " + riskResult.rejectionReason);
            }
        }

        // Calculate score
        double rawScore = totalWeight > 0 ? ((double) alignedScore / totalWeight) * 100.0 : 0.0;
        result.confluenceScore = Math.max(0.0, Math.min(100.0, rawScore));

        if (result.confluenceScore >= 75.0 && result.conflictingCount == 0) {
            result.confluenceLevel = "HIGH";
        } else if (result.confluenceScore >= 55.0 && result.conflictingCount <= 1) {
            result.confluenceLevel = "MEDIUM";
        } else {
            result.confluenceLevel = "LOW";
        }

        // Build Arabic summary
        StringBuilder sb = new StringBuilder();
        sb.append("توافق الإشارات (Confluence): ").append(result.confluenceLevel)
                .append(" (").append(String.format(Locale.US, "%.1f%%", result.confluenceScore)).append(")\n")
                .append("• مؤيدة: ").append(result.supportingCount)
                .append(" | معارضة: ").append(result.conflictingCount)
                .append(" | محايدة: ").append(result.neutralCount);

        result.summaryArabic = sb.toString();

        return result;
    }
}
