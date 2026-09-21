# PROJECT ARCHITECTURE AUDIT REPORT
**Project:** AWRIDI AI - Gold (XAU/USD) Assistant
**Date:** March 2025
**Scope:** Phase 0 Pre-Development Inspection & Architecture Baseline

---

## 1. Project Overview & Metadata

- **Language:** Java 8+
- **Package Name:** `com.awridi.ai`
- **Application Label:** `AWRIDI AI Gold`
- **Minimum SDK (minSdk):** 23 (Android 6.0 Marshmallow)
- **Target SDK (targetSdk):** 35 (Android 15)
- **Compile SDK (compileSdk):** 35
- **Version Code:** 31 (`versionName`: "3.1")
- **Build System:** Gradle (AGP 8.5.2, Gradle 8.8)

---

## 2. Core Components Audit

### A. `MainActivity.java`
- Acts as the primary UI entry point and container for navigation tabs, settings, analysis triggers, and layout management.
- Handles user interactions for loading live XAU/USD data, running paper trades, launching backtests, and viewing educational descriptions.

### B. `GoldAnalysisEngine.java`
- Multi-timeframe analysis engine fetching Twelve Data OHLCV candles (5m, 15m, 1h, 4h).
- Computes technical indicators: RSI (14), MACD (12,26,9), EMA 20/50/200, ATR (14), Pivot Support & Resistance.
- Formulates trading setup decisions (`BUY SETUP`, `SELL SETUP`, `WAIT`, `NO TRADE`) with Arabic textual rationale.

### C. `BacktestEngine.java`
- Historical strategy tester simulating trades across historic OHLCV price bars.
- Computes Win Rate, Loss Rate, Profit Factor, Max Drawdown, Net Profit, Longest Losing Streak, Average Win/Loss.

### D. `PaperTradingManager.java`
- Manages paper trading demo account balance, open positions, close transactions, and history logging saved to local `SharedPreferences`.

### E. `EncryptedPrefsHelper.java`
- Secure key-value storage using `AndroidKeyStore` AES-256 GCM authenticated encryption.
- Stores Twelve Data API Key, Telegram Token, Chat ID, and Webhook URLs securely with zero plaintext fallbacks.

### F. `TelegramNotifier.java`
- Asynchronous HTTPS dispatcher for routing Gold signal alerts, Paper Trading outcomes, and system warnings to Telegram channels/bots.

### G. `AnalysisShareHelper.java`
- Helper for copying formatted Arabic analysis to system clipboard and sharing via Android native share sheet (`ACTION_SEND`).

---

## 3. Data Integration & OHLCV Bar Representation

- **Twelve Data Integration:** Connects via REST endpoint (`https://api.twelvedata.com/time_series?symbol=XAU/USD&interval=...&apikey=...`).
- **OHLCV Structure (`GoldAnalysisEngine.Bar`):** Represents timestamp (millis/string), Open, High, Low, Close, and Volume.

---

## 4. Planned Architecture Expansion (Market Intelligence)

To upgrade AWRIDI AI into an **AI Market Intelligence Assistant for XAU/USD**, the following new modules will be added in isolated, decoupled Java classes:

1. `MarketDataProvider.java` (Interface abstraction for Twelve Data & alternative data sources).
2. `HistoricalDataEngine.java` (Raw bar cleaning, deduplication, missing candle interpolation, sorting).
3. `FeatureEngine.java` (Extraction of multi-indicator vectors: RSI, MACD, EMAs, ADX, Bollinger Bands, ATR, Volatility, Candle Body/Wicks).
4. `MarketStateEngine.java` (Market regime classification: `TREND_UP`, `TREND_DOWN`, `RANGE`, `HIGH_VOLATILITY`, `LOW_VOLATILITY`, `BREAKOUT`, `PULLBACK`, `REVERSAL`, `UNCERTAIN`).
5. `HistoricalSimilarityEngine.java` (Multi-feature Cosine/Euclidean similarity matching against historical bars without look-ahead bias).
6. `WalkForwardEngine.java` (Sliding-window In-Sample vs Out-of-Sample testing to detect overfitting).
7. `MultiTimeframeEngine.java` & `MarketContextEngine.java` (Higher timeframe 1D/4H regime alignment with 15M/5M setup execution).
8. `MarketIntelligenceBot.java` (Master coordinator synthesizing historical similarity, regime context, empirical sample size probabilities, and risk rules into a unified decision).
9. `RiskIntelligenceEngine.java` (Capital protection rules, max daily drawdown limits, position sizing, SL/TP execution guards).
10. `TradeJournalEngine.java` (Closed-loop learning memory tracking MFE, MAE, regime, pattern, and trade outcome).
11. `SubscriptionTier.java` (Feature-gating architecture for `FREE`, `PRO`, `PREMIUM`).

---

## 5. Security & Build Readiness

- **ADB Backup:** Disabled (`android:allowBackup="false"`).
- **Obfuscation:** ProGuard/R8 enabled for release buildType with custom rules in `proguard-rules.pro`.
- **Git Hygiene:** Build artifacts (`.gradle/` and `app/build/`) ignored via `.gitignore`.
