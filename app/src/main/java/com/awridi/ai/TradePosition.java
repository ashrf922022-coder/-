package com.awridi.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * TradePosition
 * Core model representing a Paper Trade Position for XAU/USD.
 */
public class TradePosition {

    public enum Direction {
        BUY,
        SELL,
        NONE
    }

    public enum Status {
        PENDING,
        OPEN,
        TP_HIT,
        SL_HIT,
        CLOSED,
        CANCELLED,
        REJECTED,
        BLOCKED
    }

    public String tradeId;
    public String symbol = MainActivity.GOLD_SYMBOL;
    public Direction direction = Direction.BUY;
    public AIDecisionResult.Decision decision = AIDecisionResult.Decision.BUY;
    public Status status = Status.PENDING;

    public double entryPrice = 0.0;
    public double stopLoss = 0.0;
    public double takeProfit = 0.0;
    public double positionSize = 0.1; // Lots
    public double riskAmount = 0.0;   // USD
    public double riskRewardRatio = 0.0;

    public String openTime = "";
    public String closeTime = "";
    public String candleTime = "";

    public double currentPrice = 0.0;
    public double unrealizedPnL = 0.0;
    public double realizedPnL = 0.0;
    public double rMultiple = 0.0;

    public MarketRegimeResult.Regime marketRegime = MarketRegimeResult.Regime.UNCERTAIN;
    public double confluenceScore = 0.0;
    public double confidence = 0.0;
    public AIDecisionResult.TradeQuality tradeQuality = AIDecisionResult.TradeQuality.LOW_QUALITY;

    public String reason = "";
    public String rejectionReason = "";
    public String sourceDecisionId = "";

    public String conflictType = "NONE"; // e.g. "TP_SL_CONFLICT"
    public String resolution = "NONE";   // e.g. "CONSERVATIVE_SL_FIRST"

    public double mae = 0.0; // Maximum Adverse Excursion
    public double mfe = 0.0; // Maximum Favorable Excursion

    public TradePosition() {
        this.tradeId = "TP_" + UUID.randomUUID().toString().substring(0, 8);
        this.openTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("tradeId", tradeId != null ? tradeId : "");
            json.put("symbol", symbol != null ? symbol : MainActivity.GOLD_SYMBOL);
            json.put("direction", direction != null ? direction.name() : Direction.BUY.name());
            json.put("decision", decision != null ? decision.name() : AIDecisionResult.Decision.BUY.name());
            json.put("status", status != null ? status.name() : Status.PENDING.name());

            json.put("entryPrice", entryPrice);
            json.put("stopLoss", stopLoss);
            json.put("takeProfit", takeProfit);
            json.put("positionSize", positionSize);
            json.put("riskAmount", riskAmount);
            json.put("riskRewardRatio", riskRewardRatio);

            json.put("openTime", openTime != null ? openTime : "");
            json.put("closeTime", closeTime != null ? closeTime : "");
            json.put("candleTime", candleTime != null ? candleTime : "");

            json.put("currentPrice", currentPrice);
            json.put("unrealizedPnL", unrealizedPnL);
            json.put("realizedPnL", realizedPnL);
            json.put("rMultiple", rMultiple);

            json.put("marketRegime", marketRegime != null ? marketRegime.name() : MarketRegimeResult.Regime.UNCERTAIN.name());
            json.put("confluenceScore", confluenceScore);
            json.put("confidence", confidence);
            json.put("tradeQuality", tradeQuality != null ? tradeQuality.name() : AIDecisionResult.TradeQuality.LOW_QUALITY.name());

            json.put("reason", reason != null ? reason : "");
            json.put("rejectionReason", rejectionReason != null ? rejectionReason : "");
            json.put("sourceDecisionId", sourceDecisionId != null ? sourceDecisionId : "");

            json.put("conflictType", conflictType != null ? conflictType : "NONE");
            json.put("resolution", resolution != null ? resolution : "NONE");

            json.put("mae", mae);
            json.put("mfe", mfe);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static TradePosition fromJsonObject(JSONObject json) {
        TradePosition t = new TradePosition();
        if (json == null) return t;

        try {
            t.tradeId = json.optString("tradeId", t.tradeId);
            t.symbol = json.optString("symbol", MainActivity.GOLD_SYMBOL);
            t.direction = Direction.valueOf(json.optString("direction", "BUY"));
            t.decision = AIDecisionResult.Decision.valueOf(json.optString("decision", "BUY"));
            t.status = Status.valueOf(json.optString("status", "PENDING"));

            t.entryPrice = json.optDouble("entryPrice", 0.0);
            t.stopLoss = json.optDouble("stopLoss", 0.0);
            t.takeProfit = json.optDouble("takeProfit", 0.0);
            t.positionSize = json.optDouble("positionSize", 0.1);
            t.riskAmount = json.optDouble("riskAmount", 0.0);
            t.riskRewardRatio = json.optDouble("riskRewardRatio", 0.0);

            t.openTime = json.optString("openTime", "");
            t.closeTime = json.optString("closeTime", "");
            t.candleTime = json.optString("candleTime", "");

            t.currentPrice = json.optDouble("currentPrice", 0.0);
            t.unrealizedPnL = json.optDouble("unrealizedPnL", 0.0);
            t.realizedPnL = json.optDouble("realizedPnL", 0.0);
            t.rMultiple = json.optDouble("rMultiple", 0.0);

            try {
                t.marketRegime = MarketRegimeResult.Regime.valueOf(json.optString("marketRegime", "UNCERTAIN"));
            } catch (Exception ignored) {
                t.marketRegime = MarketRegimeResult.Regime.UNCERTAIN;
            }

            t.confluenceScore = json.optDouble("confluenceScore", 0.0);
            t.confidence = json.optDouble("confidence", 0.0);

            try {
                t.tradeQuality = AIDecisionResult.TradeQuality.valueOf(json.optString("tradeQuality", "LOW_QUALITY"));
            } catch (Exception ignored) {
                t.tradeQuality = AIDecisionResult.TradeQuality.LOW_QUALITY;
            }

            t.reason = json.optString("reason", "");
            t.rejectionReason = json.optString("rejectionReason", "");
            t.sourceDecisionId = json.optString("sourceDecisionId", "");

            t.conflictType = json.optString("conflictType", "NONE");
            t.resolution = json.optString("resolution", "NONE");

            t.mae = json.optDouble("mae", 0.0);
            t.mfe = json.optDouble("mfe", 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return t;
    }

    @Override
    public String toString() {
        return "TradePosition{" +
                "id='" + tradeId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", dir=" + direction +
                ", status=" + status +
                ", entry=" + String.format(Locale.US, "%.2f", entryPrice) +
                ", SL=" + String.format(Locale.US, "%.2f", stopLoss) +
                ", TP=" + String.format(Locale.US, "%.2f", takeProfit) +
                ", size=" + String.format(Locale.US, "%.2f", positionSize) +
                ", realizedPnL=" + String.format(Locale.US, "%+.2f", realizedPnL) +
                ", rMultiple=" + String.format(Locale.US, "%+.2f", rMultiple) +
                '}';
    }
}
