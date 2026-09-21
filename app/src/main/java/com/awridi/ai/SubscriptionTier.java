package com.awridi.ai;

import android.content.SharedPreferences;

/**
 * Subscription Tier Management Model supporting feature gating across Free, Pro, and Premium levels.
 */
public class SubscriptionTier {

    public enum Tier {
        FREE,
        PRO,
        PREMIUM
    }

    private static final String PREF_KEY_TIER = "awridi_subscription_tier";

    public static Tier getCurrentTier(SharedPreferences prefs) {
        if (prefs == null) return Tier.FREE;
        String name = prefs.getString(PREF_KEY_TIER, "FREE");
        try {
            return Tier.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Tier.FREE;
        }
    }

    public static void setTier(SharedPreferences prefs, Tier tier) {
        if (prefs == null || tier == null) return;
        prefs.edit().putString(PREF_KEY_TIER, tier.name()).apply();
    }

    public static boolean canAccessHistoricalSimilarity(Tier tier) {
        return tier == Tier.PRO || tier == Tier.PREMIUM;
    }

    public static boolean canAccessWalkForward(Tier tier) {
        return tier == Tier.PREMIUM;
    }

    public static boolean canAccessTradeJournal(Tier tier) {
        return tier == Tier.PRO || tier == Tier.PREMIUM;
    }
}
