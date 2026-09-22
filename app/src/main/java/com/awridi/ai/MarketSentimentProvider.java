package com.awridi.ai;

import java.util.List;

/**
 * Interface abstraction for future Market News / Sentiment API integration.
 * Ensures zero fake news or fabricated sentiment is generated.
 */
public interface MarketSentimentProvider {

    class SentimentData {
        public final String source;
        public final double sentimentScore; // -1.0 (Extreme Bearish) to +1.0 (Extreme Bullish)
        public final String summaryArabic;
        public final long timestamp;

        public SentimentData(String source, double sentimentScore, String summaryArabic, long timestamp) {
            this.source = source;
            this.sentimentScore = sentimentScore;
            this.summaryArabic = summaryArabic;
            this.timestamp = timestamp;
        }
    }

    interface Callback {
        void onSuccess(List<SentimentData> newsList);
        void onError(String errorMessage);
    }

    void fetchLatestGoldNewsAsync(String apiKey, Callback callback);
}
