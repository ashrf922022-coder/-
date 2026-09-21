package com.awridi.ai;

import java.util.List;

/**
 * Abstraction layer for market data providers.
 * Allows switching between Twelve Data and other data providers without modifying core intelligence engines.
 */
public interface MarketDataProvider {

    interface Callback {
        void onSuccess(String interval, List<GoldAnalysisEngine.Bar> bars);
        void onError(String interval, String errorMessage);
    }

    /**
     * Fetches historical OHLCV series for a given symbol and timeframe interval.
     *
     * @param symbol Trading symbol (e.g. "XAU/USD")
     * @param interval Timeframe interval (e.g. "5min", "15min", "1h", "4h", "1day")
     * @param outputSize Number of bars to retrieve
     * @param apiKey API key for the provider
     * @param callback Callback for asynchronous response handling
     */
    void fetchHistoricalBars(String symbol, String interval, int outputSize, String apiKey, Callback callback);

    /**
     * Synchronous fetch attempt with built-in rate-limit and error safety.
     */
    List<GoldAnalysisEngine.Bar> fetchHistoricalBarsSync(String symbol, String interval, int outputSize, String apiKey) throws Exception;
}
