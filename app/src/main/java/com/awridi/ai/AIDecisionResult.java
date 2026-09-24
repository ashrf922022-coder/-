package com.awridi.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AIDecisionResult {

    public enum Decision {
        BUY,
        SELL,
        WAIT
    }

    public enum TradeQuality {
        HIGH_QUALITY("عالية الجودة (HIGH QUALITY)"),
        MEDIUM_QUALITY("متوسطة الجودة (MEDIUM QUALITY)"),
        LOW_QUALITY("منخفضة الجودة (LOW QUALITY)");

        private final String arabicName;

        TradeQuality(String arabicName) {
            this.arabicName = arabicName;
        }

        public String getArabicName() {
            return arabicName;
        }
    }

    public String decisionId = "DEC_" + System.currentTimeMillis();
    public Decision decision = Decision.WAIT;
    public String symbol = "XAU/USD";
    public String timeframe = "15min";
    public long timestamp = System.currentTimeMillis();
    public double currentPrice = 0.0;

    // Signal Confluence
    public double confluenceScore = 0.0; // 0.0 to 100.0
    public String confluenceLevel = "LOW"; // "HIGH", "MEDIUM", "LOW"
    public int supportingSignalsCount = 0;
    public int conflictingSignalsCount = 0;
    public int neutralSignalsCount = 0;
    public List<String> supportingSignals = new ArrayList<>();
    public List<String> conflictingSignals = new ArrayList<>();
    public List<String> neutralSignals = new ArrayList<>();

    // Market Regime
    public MarketRegimeResult.Regime marketRegime = MarketRegimeResult.Regime.UNCERTAIN;
    public double marketRegimeConfidence = 0.0;
    public String marketRegimeNameArabic = "غير محدد";

    // Trade Quality Score
    public double tradeQualityScore = 0.0; // 0.0 to 100.0
    public TradeQuality tradeQuality = TradeQuality.LOW_QUALITY;

    // Overall Confidence System
    public double confidence = 0.0; // 0.0 to 100.0 %
    public String confidenceExplanation = "";

    // Risk Gate Evaluation
    public boolean riskApproved = false;
    public String rejectionReason = "";
    public List<String> riskWarnings = new ArrayList<>();

    // Recommended Execution Setup (PAPER / MOCK ONLY)
    public double entryPrice = 0.0;
    public double stopLoss = 0.0;
    public double takeProfit = 0.0;
    public double riskRewardRatio = 0.0;
    public double positionSizeLot = 0.0;

    // Explanations & Decision Audit Trail
    public List<String> decisionReasons = new ArrayList<>();
    public List<String> auditTrail = new ArrayList<>();
    public String arabicExplanation = "";

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("decisionId", decisionId);
            json.put("decision", decision.name());
            json.put("symbol", symbol);
            json.put("timeframe", timeframe);
            json.put("timestamp", timestamp);
            json.put("currentPrice", currentPrice);

            json.put("confluenceScore", confluenceScore);
            json.put("confluenceLevel", confluenceLevel);
            json.put("supportingSignalsCount", supportingSignalsCount);
            json.put("conflictingSignalsCount", conflictingSignalsCount);
            json.put("neutralSignalsCount", neutralSignalsCount);
            json.put("supportingSignals", new JSONArray(supportingSignals));
            json.put("conflictingSignals", new JSONArray(conflictingSignals));
            json.put("neutralSignals", new JSONArray(neutralSignals));

            json.put("marketRegime", marketRegime != null ? marketRegime.name() : MarketRegimeResult.Regime.UNCERTAIN.name());
            json.put("marketRegimeConfidence", marketRegimeConfidence);
            json.put("marketRegimeNameArabic", marketRegimeNameArabic);

            json.put("tradeQualityScore", tradeQualityScore);
            json.put("tradeQuality", tradeQuality != null ? tradeQuality.name() : TradeQuality.LOW_QUALITY.name());

            json.put("confidence", confidence);
            json.put("confidenceExplanation", confidenceExplanation);

            json.put("riskApproved", riskApproved);
            json.put("rejectionReason", rejectionReason);
            json.put("riskWarnings", new JSONArray(riskWarnings));

            json.put("entryPrice", entryPrice);
            json.put("stopLoss", stopLoss);
            json.put("takeProfit", takeProfit);
            json.put("riskRewardRatio", riskRewardRatio);
            json.put("positionSizeLot", positionSizeLot);

            json.put("decisionReasons", new JSONArray(decisionReasons));
            json.put("auditTrail", new JSONArray(auditTrail));
            json.put("arabicExplanation", arabicExplanation);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static AIDecisionResult fromJsonObject(JSONObject json) {
        AIDecisionResult res = new AIDecisionResult();
        if (json == null) return res;

        try {
            res.decisionId = json.optString("decisionId", "DEC_" + System.currentTimeMillis());
            res.decision = Decision.valueOf(json.optString("decision", "WAIT"));
            res.symbol = json.optString("symbol", "XAU/USD");
            res.timeframe = json.optString("timeframe", "15min");
            res.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            res.currentPrice = json.optDouble("currentPrice", 0.0);

            res.confluenceScore = json.optDouble("confluenceScore", 0.0);
            res.confluenceLevel = json.optString("confluenceLevel", "LOW");
            res.supportingSignalsCount = json.optInt("supportingSignalsCount", 0);
            res.conflictingSignalsCount = json.optInt("conflictingSignalsCount", 0);
            res.neutralSignalsCount = json.optInt("neutralSignalsCount", 0);

            res.supportingSignals = jsonArrayToList(json.optJSONArray("supportingSignals"));
            res.conflictingSignals = jsonArrayToList(json.optJSONArray("conflictingSignals"));
            res.neutralSignals = jsonArrayToList(json.optJSONArray("neutralSignals"));

            String regimeStr = json.optString("marketRegime", "UNCERTAIN");
            try {
                res.marketRegime = MarketRegimeResult.Regime.valueOf(regimeStr);
            } catch (Exception e) {
                res.marketRegime = MarketRegimeResult.Regime.UNCERTAIN;
            }
            res.marketRegimeConfidence = json.optDouble("marketRegimeConfidence", 0.0);
            res.marketRegimeNameArabic = json.optString("marketRegimeNameArabic", "غير محدد");

            res.tradeQualityScore = json.optDouble("tradeQualityScore", 0.0);
            String qStr = json.optString("tradeQuality", "LOW_QUALITY");
            try {
                res.tradeQuality = TradeQuality.valueOf(qStr);
            } catch (Exception e) {
                res.tradeQuality = TradeQuality.LOW_QUALITY;
            }

            res.confidence = json.optDouble("confidence", 0.0);
            res.confidenceExplanation = json.optString("confidenceExplanation", "");

            res.riskApproved = json.optBoolean("riskApproved", false);
            res.rejectionReason = json.optString("rejectionReason", "");
            res.riskWarnings = jsonArrayToList(json.optJSONArray("riskWarnings"));

            res.entryPrice = json.optDouble("entryPrice", 0.0);
            res.stopLoss = json.optDouble("stopLoss", 0.0);
            res.takeProfit = json.optDouble("takeProfit", 0.0);
            res.riskRewardRatio = json.optDouble("riskRewardRatio", 0.0);
            res.positionSizeLot = json.optDouble("positionSizeLot", 0.0);

            res.decisionReasons = jsonArrayToList(json.optJSONArray("decisionReasons"));
            res.auditTrail = jsonArrayToList(json.optJSONArray("auditTrail"));
            res.arabicExplanation = json.optString("arabicExplanation", "");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }

    private static List<String> jsonArrayToList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i, ""));
            }
        }
        return list;
    }

    @Override
    public String toString() {
        return "AIDecisionResult{" +
                "decisionId='" + decisionId + '\'' +
                ", decision=" + decision +
                ", symbol='" + symbol + '\'' +
                ", confidence=" + String.format(Locale.US, "%.1f%%", confidence) +
                ", confluenceScore=" + String.format(Locale.US, "%.1f", confluenceScore) +
                ", tradeQuality=" + tradeQuality +
                ", marketRegime=" + (marketRegime != null ? marketRegime.name() : "NULL") +
                ", riskApproved=" + riskApproved +
                '}';
    }
}
