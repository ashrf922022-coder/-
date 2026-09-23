package com.awridi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TradeSetup {

    public enum Direction {
        BUY,
        SELL,
        NONE
    }

    public Direction direction = Direction.NONE;
    public double entryPrice = 0.0;
    public double stopLoss = 0.0;
    public double takeProfit = 0.0;
    public double riskDistance = 0.0;
    public double rewardDistance = 0.0;
    public double riskRewardRatio = 0.0;
    public double confidence = 0.0; // Confidence % (0.0 to 100.0)
    public String signalQuality = "INVALID"; // "HIGH", "MEDIUM", "LOW", "INVALID"
    public MarketRegimeResult.Regime marketRegime = MarketRegimeResult.Regime.UNCERTAIN;
    public List<String> supportingFactors = new ArrayList<>();
    public List<String> conflictingFactors = new ArrayList<>();
    public String explanation = "";
    public boolean valid = false;

    @Override
    public String toString() {
        return "TradeSetup{" +
                "direction=" + direction +
                ", entryPrice=" + String.format(Locale.US, "%.2f", entryPrice) +
                ", stopLoss=" + String.format(Locale.US, "%.2f", stopLoss) +
                ", takeProfit=" + String.format(Locale.US, "%.2f", takeProfit) +
                ", riskDistance=" + String.format(Locale.US, "%.2f", riskDistance) +
                ", rewardDistance=" + String.format(Locale.US, "%.2f", rewardDistance) +
                ", riskRewardRatio=" + String.format(Locale.US, "%.2f", riskRewardRatio) +
                ", confidence=" + String.format(Locale.US, "%.1f%%", confidence) +
                ", signalQuality='" + signalQuality + '\'' +
                ", marketRegime=" + marketRegime +
                ", valid=" + valid +
                ", supportingFactors=" + supportingFactors.size() +
                ", conflictingFactors=" + conflictingFactors.size() +
                '}';
    }
}
