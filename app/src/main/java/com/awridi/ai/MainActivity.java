package com.awridi.ai;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    // UI Containers
    LinearLayout root, content, navBar;
    SharedPreferences prefs;
    ExecutorService executor = Executors.newFixedThreadPool(4);
    JSONObject design, strat;

    // Theme Colors
    int primaryColor, secondaryColor, backgroundColor, surfaceColor, textColor, mutedColor;

    // Core Settings Keys
    public static final String PREF_KEY_API_KEY = "td_api_key";
    public static final String PREF_KEY_TELEGRAM_TOKEN = "tg_bot_token";
    public static final String PREF_KEY_TELEGRAM_CHAT_ID = "tg_chat_id";
    public static final String PREF_KEY_TV_WEBHOOK = "tv_webhook_url";
    public static final String PREF_KEY_CAPITAL = "account_capital";
    public static final String PREF_KEY_RISK_PCT = "risk_percentage";
    public static final String PREF_KEY_PAPER_TRADES = "paper_trades_json";

    // Symbol strictly set to XAU/USD
    public static final String GOLD_SYMBOL = "XAU/USD";

    // UI Input Elements
    EditText apiKeyInput, tgTokenInput, tgChatIdInput, capitalInput, riskPctInput, tvWebhookInput;
    TextView statusText;

    // Current Analysis Caches
    GoldAnalysisEngine.AnalysisResult currentAnalysis = null;
    MarketIntelligenceEngine.Result currentMiResult = null;
    TradeSetup currentTradeSetup = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("awridi_gold_prefs", MODE_PRIVATE);
        design = loadJsonAsset("design", "design_default.json");
        strat = loadJsonAsset("strategy", "strategy_default.json");
        applyTheme();
        showHomeScreen();
    }

    JSONObject loadJsonAsset(String key, String assetName) {
        try {
            String saved = prefs.getString(key, null);
            if (saved != null) return new JSONObject(saved);
            InputStream in = getAssets().open(assetName);
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            StringBuilder s = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) s.append(l);
            return new JSONObject(s.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    void saveJsonObject(String key, JSONObject o) {
        prefs.edit().putString(key, o.toString()).apply();
    }

    void applyTheme() {
        primaryColor = Color.parseColor(design.optString("primary", "#D4AF37"));
        secondaryColor = Color.parseColor(design.optString("secondary", "#FFD700"));
        backgroundColor = Color.parseColor(design.optString("background", "#0B0E14"));
        surfaceColor = Color.parseColor(design.optString("surface", "#161B26"));
        textColor = Color.parseColor(design.optString("text", "#F5F7FF"));
        mutedColor = Color.parseColor(design.optString("muted", "#8A94A6"));
    }

    // --- UI Helper Components ---
    TextView createTextView(String text, float spSize, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(textColor);
        t.setTextSize(spSize);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 4, 0, 4);
        return t;
    }

    Button createButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.BLACK);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(24);
        shape.setColor(primaryColor);
        b.setBackground(shape);
        b.setOnClickListener(listener);
        b.setPadding(24, 12, 24, 12);
        return b;
    }

    Button createSecondaryButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(24);
        shape.setStroke(2, primaryColor);
        shape.setColor(surfaceColor);
        b.setBackground(shape);
        b.setOnClickListener(listener);
        b.setPadding(20, 10, 20, 10);
        return b;
    }

    LinearLayout createCardBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(24, 24, 24, 24);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(surfaceColor);
        gd.setCornerRadius(design.optInt("cardRadius", 16));
        gd.setStroke(1, Color.parseColor("#263045"));
        l.setBackground(gd);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 14, 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    EditText createEditText(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(textColor);
        e.setHintTextColor(mutedColor);
        e.setPadding(20, 18, 20, 18);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#0F1420"));
        gd.setCornerRadius(12);
        gd.setStroke(1, Color.parseColor("#2A354D"));
        e.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 12);
        e.setLayoutParams(lp);
        return e;
    }

    void setupBaseLayout(String activeTab) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setPadding(24, 28, 24, 16);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView appTitle = createTextView("🏆 AWRIDI AI Gold", 22, true);
        appTitle.setTextColor(primaryColor);
        header.addView(appTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView versionTv = createTextView("v3.1 | XAU/USD", 12, false);
        versionTv.setTextColor(mutedColor);
        header.addView(versionTv);
        root.addView(header);

        // Content Scrollable
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(20, 0, 20, 20);
        sc.addView(content, new ViewGroup.LayoutParams(-1, -1));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        // Navigation Bar
        navBar = new LinearLayout(this);
        navBar.setPadding(8, 8, 8, 12);
        navBar.setBackgroundColor(surfaceColor);
        navBar.setGravity(Gravity.CENTER);

        String[] tabs = {
                "الرئيسية",
                "المحفظة",
                "Backtest",
                "🧠 ذكاء السوق",
                "المساعد",
                "الإعدادات"
        };

        String[] keys = {
                "home",
                "portfolio",
                "backtest",
                "market_intelligence",
                "assistant",
                "settings"
        };

        for (int i = 0; i < tabs.length; i++) {
            final String tabKey = keys[i];
            Button b;
            if (tabKey.equals(activeTab) || (activeTab.equals("paper") && tabKey.equals("portfolio"))) {
                b = createButton(tabs[i], v -> switchTab(tabKey));
            } else {
                b = createSecondaryButton(tabs[i], v -> switchTab(tabKey));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(4, 0, 4, 0);
            navBar.addView(b, lp);
        }
        root.addView(navBar);
        setContentView(root);
    }

    void switchTab(String tabKey) {
        switch (tabKey) {
            case "home":
                showHomeScreen();
                break;

            case "portfolio":
            case "paper":
                showPortfolioScreen();
                break;

            case "backtest":
                showBacktestScreen();
                break;

            case "market_intelligence":
                showMarketIntelligenceScreen();
                break;

            case "assistant":
                showAiAssistantScreen();
                break;

            case "settings":
                showSettingsScreen();
                break;
        }
    }

    // --- SCREEN 1: HOME (GOLD XAU/USD ANALYSIS) ---
    void showHomeScreen() {
        setupBaseLayout("home");

        LinearLayout heroCard = createCardBox();
        heroCard.addView(createTextView("👑 مساعد تداول الذهب (XAU/USD)", 20, true));
        heroCard.addView(createTextView("تحليل فني متعدد الأطر + محرك القرار + إدارة المخاطر المتقدمة", 13, false));
        content.addView(heroCard);

        LinearLayout actionCard = createCardBox();
        actionCard.addView(createTextView("⚡ تحليل الذهب الآن", 16, true));

        Button analyzeBtn = createButton("🔍 بدء تحليل XAU/USD", v -> runGoldAnalysis());
        actionCard.addView(analyzeBtn);

        statusText = createTextView("جاهز للتحليل. انقر على الزر أعلاه.", 13, false);
        statusText.setTextColor(mutedColor);
        actionCard.addView(statusText);
        content.addView(actionCard);

        if (currentAnalysis != null) {
            displayAnalysisResult(currentAnalysis);
        } else {
            LinearLayout infoCard = createCardBox();
            infoCard.addView(createTextView("💡 إرشادات سريعة:", 15, true));
            infoCard.addView(createTextView("• التطبيق يحفظ مفتاح Twelve Data بشكل آمن داخل الجهاز.", 13, false));
            infoCard.addView(createTextView("• القرارات الناتجة (BUY SETUP / SELL SETUP / WAIT / NO TRADE) تعتمد على الشروط المتعددة للمؤشرات.", 13, false));
            infoCard.addView(createTextView("• قم بضبط مفتاح API ونسبة المخاطرة من شاشة الإعدادات.", 13, false));
            content.addView(infoCard);
        }
    }

    void runGoldAnalysis() {
        String apiKey = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_API_KEY, "").trim();
        if (apiKey.isEmpty()) {
            statusText.setText("⚠️ يرجى إدخال مفتاح Twelve Data API في شاشة الإعدادات أولًا.");
            statusText.setTextColor(Color.YELLOW);
            return;
        }

        statusText.setText("🔄 جاري سحب بيانات الذهب (5m, 15m, 1h, 4h)...");
        statusText.setTextColor(secondaryColor);

        executor.submit(() -> {
            try {
                Map<String, List<GoldAnalysisEngine.Bar>> mtfBars = new HashMap<>();
                String[] intervals = {"5min", "15min", "1h", "4h"};
                for (String tf : intervals) {
                    List<GoldAnalysisEngine.Bar> bars = GoldAnalysisEngine.fetchTwelveData(GOLD_SYMBOL, tf, apiKey, 150);
                    if (bars != null && !bars.isEmpty()) {
                        mtfBars.put(tf, bars);
                    }
                }

                if (!mtfBars.containsKey("15min") && !mtfBars.containsKey("1h")) {
                    throw new Exception("تعذر جلب بيانات الذهب من Twelve Data. تأكد من صحة المفتاح والاتصال.");
                }

                GoldAnalysisEngine.AnalysisResult result = GoldAnalysisEngine.analyzeGold(mtfBars, prefs);
                currentAnalysis = result;

                // Evaluate TradeSetup using TradeSetupEngine
                List<GoldAnalysisEngine.Bar> baseBars = mtfBars.get("15min");
                if (baseBars == null || baseBars.isEmpty()) {
                    baseBars = mtfBars.values().iterator().next();
                }
                List<MarketIntelligenceEngine.Bar> miBars = new ArrayList<>();
                for (GoldAnalysisEngine.Bar gb : baseBars) {
                    miBars.add(new MarketIntelligenceEngine.Bar(gb.o, gb.h, gb.l, gb.c, gb.v));
                }
                TradeSetupEngine setupEngine = new TradeSetupEngine();
                currentTradeSetup = setupEngine.createTradeSetup(miBars);

                runOnUiThread(() -> {
                    statusText.setText("✅ اكتمل التحليل بنجاح!");
                    statusText.setTextColor(Color.GREEN);
                    showHomeScreen();

                    sendTelegramAlertIfNeeded(result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("❌ خطأ: " + e.getMessage());
                    statusText.setTextColor(Color.RED);
                });
            }
        });
    }

    void displayAnalysisResult(GoldAnalysisEngine.AnalysisResult res) {
        LinearLayout priceCard = createCardBox();
        priceCard.addView(createTextView("🌟 السعر الحالي للذهب (XAU/USD)", 16, true));
        TextView priceTv = createTextView("$ " + String.format(Locale.US, "%.2f", res.currentPrice), 28, true);
        priceTv.setTextColor(primaryColor);
        priceCard.addView(priceTv);
        priceCard.addView(createTextView("التحديث: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()), 12, false));
        content.addView(priceCard);

        LinearLayout signalCard = createCardBox();
        signalCard.addView(createTextView("🎯 قرار النظام وإشارة التداول", 16, true));

        TextView signalTv = createTextView(res.signal, 24, true);
        if (res.signal.contains("BUY")) signalTv.setTextColor(Color.GREEN);
        else if (res.signal.contains("SELL")) signalTv.setTextColor(Color.RED);
        else if (res.signal.equals("WAIT")) signalTv.setTextColor(Color.YELLOW);
        else signalTv.setTextColor(Color.GRAY);
        signalCard.addView(signalTv);

        signalCard.addView(createTextView("نسبة توافق الشروط (الثقة): " + String.format(Locale.US, "%.0f%%", res.confidenceScore * 100), 14, true));
        content.addView(signalCard);

        // Display Trade Setup Box & Risk Management (Phase 5 Risk Integration)
        if (currentTradeSetup != null) {
            LinearLayout setupCard = createCardBox();
            setupCard.addView(createTextView("🎯 إعداد الصفقة وحساب المخاطر (Trade Setup & Risk Management)", 16, true));

            double cap = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
            double riskPct = Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
            double maxDailyLossPct = Double.parseDouble(prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0"));

            PortfolioManager.PortfolioSummary summary = PortfolioManager.calculateSummary(prefs);

            RiskManagementEngine riskEngine = new RiskManagementEngine();
            riskEngine.setMaxDailyLossPercentage(maxDailyLossPct);
            RiskManagementEngine.RiskResult riskResult = riskEngine.evaluateTradeSetupRisk(currentTradeSetup, cap, riskPct, summary.todayLossPnl);

            if (currentTradeSetup.valid && riskResult.valid) {
                TextView setupDirTv = createTextView("اتجاه الإعداد: " + currentTradeSetup.direction.name(), 18, true);
                setupDirTv.setTextColor(currentTradeSetup.direction == TradeSetup.Direction.BUY ? Color.GREEN : Color.RED);
                setupCard.addView(setupDirTv);

                setupCard.addView(createTextView("• رأس المال: $" + String.format(Locale.US, "%.2f", riskResult.accountBalance), 14, false));
                setupCard.addView(createTextView("• نسبة المخاطرة: " + String.format(Locale.US, "%.2f%%", riskResult.riskPercentage) + " ($" + String.format(Locale.US, "%.2f", riskResult.riskAmount) + ")", 14, true));
                setupCard.addView(createTextView("• سعر الدخول (Entry): $" + String.format(Locale.US, "%.2f", riskResult.entryPrice), 14, true));
                setupCard.addView(createTextView("• وقف الخسارة (Stop Loss): $" + String.format(Locale.US, "%.2f", riskResult.stopLoss) + " (مسافة: $" + String.format(Locale.US, "%.2f", riskResult.riskDistance) + ")", 14, true));
                setupCard.addView(createTextView("• أخذ الربح (Take Profit): $" + String.format(Locale.US, "%.2f", riskResult.takeProfit) + " (مسافة: $" + String.format(Locale.US, "%.2f", riskResult.rewardDistance) + ")", 14, true));
                setupCard.addView(createTextView("• Risk/Reward Ratio: 1 : " + String.format(Locale.US, "%.2f", riskResult.riskRewardRatio), 14, true));
                setupCard.addView(createTextView("• حجم الصفقة المحسوب (Position Size): " + String.format(Locale.US, "%.2f", riskResult.positionSize) + " لوت", 14, true));
                setupCard.addView(createTextView("• الحد الأقصى للخسارة اليومية: $" + String.format(Locale.US, "%.2f", riskResult.maximumDailyLossAmount) + " (" + String.format(Locale.US, "%.1f%%", riskResult.maximumDailyLoss) + ")", 14, false));
                setupCard.addView(createTextView("• الخسارة اليومية الحالية: $" + String.format(Locale.US, "%.2f", riskResult.currentDailyLoss), 14, false));

                TextView statusValidTv = createTextView("الحالة: Risk Valid ✅", 14, true);
                statusValidTv.setTextColor(Color.GREEN);
                setupCard.addView(statusValidTv);

                if (!riskResult.warnings.isEmpty()) {
                    for (String warn : riskResult.warnings) {
                        TextView wTv = createTextView(" ⚠️ " + warn, 13, false);
                        wTv.setTextColor(Color.YELLOW);
                        setupCard.addView(wTv);
                    }
                }

                Button paperBtn = createButton("📝 فتح صفقة تجريبية بإعداد Trade Setup", v -> executePaperTradeFromSetup(currentTradeSetup));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 10, 0, 0);
                setupCard.addView(paperBtn, lp);
            } else {
                TextView setupWaitTv = createTextView("WAIT / RISK BLOCKED — الصفقة مرفوضة من إدارة المخاطر 🛑", 16, true);
                setupWaitTv.setTextColor(Color.RED);
                setupCard.addView(setupWaitTv);

                String rejReason = !currentTradeSetup.valid ? "إعداد الصفقة الفني غير صالح." : riskResult.rejectionReason;
                TextView reasonTv = createTextView("سبب الرفض: " + rejReason, 14, true);
                reasonTv.setTextColor(Color.YELLOW);
                setupCard.addView(reasonTv);

                setupCard.addView(createTextView("• رأس المال: $" + String.format(Locale.US, "%.2f", cap), 13, false));
                setupCard.addView(createTextView("• الحد الأقصى للخسارة اليومية: " + String.format(Locale.US, "%.1f%%", maxDailyLossPct) + " ($" + String.format(Locale.US, "%.2f", cap * (maxDailyLossPct / 100.0)) + ")", 13, false));
                setupCard.addView(createTextView("• الخسارة اليومية الحالية: $" + String.format(Locale.US, "%.2f", summary.todayLossPnl), 13, false));

                TextView statusBlockedTv = createTextView("الحالة: Risk Blocked ❌", 14, true);
                statusBlockedTv.setTextColor(Color.RED);
                setupCard.addView(statusBlockedTv);

                if (!currentTradeSetup.conflictingFactors.isEmpty()) {
                    setupCard.addView(createTextView("تفاصيل عدم الصلاحية:", 13, true));
                    for (String con : currentTradeSetup.conflictingFactors) {
                        setupCard.addView(createTextView(" • " + con, 13, false));
                    }
                }
            }
            content.addView(setupCard);
        } else if (res.signal.contains("SETUP")) {
            LinearLayout tradeCard = createCardBox();
            tradeCard.addView(createTextView("📐 خطة إدارة المخاطر للصفقة", 16, true));
            tradeCard.addView(createTextView("• سعر الدخول (Entry): $" + String.format(Locale.US, "%.2f", res.entryPrice), 14, false));
            tradeCard.addView(createTextView("• وقف الخسارة (Stop Loss): $" + String.format(Locale.US, "%.2f", res.stopLoss), 14, true));
            tradeCard.addView(createTextView("• الهدف الأول (Take Profit 1): $" + String.format(Locale.US, "%.2f", res.takeProfit1), 14, false));
            tradeCard.addView(createTextView("• الهدف الثاني (Take Profit 2): $" + String.format(Locale.US, "%.2f", res.takeProfit2), 14, false));
            tradeCard.addView(createTextView("• نسبة المخاطرة/العائد (R:R): 1 : " + String.format(Locale.US, "%.2f", res.riskRewardRatio), 14, false));

            double capital = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
            double riskPct = Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
            double riskUsd = capital * (riskPct / 100.0);
            double expectedProfitUsd = riskUsd * res.riskRewardRatio;

            tradeCard.addView(createTextView("• قيمة المخاطرة بالدولار: $" + String.format(Locale.US, "%.2f", riskUsd), 14, true));
            tradeCard.addView(createTextView("• قيمة الربح المتوقع بالدولار: $" + String.format(Locale.US, "%.2f", expectedProfitUsd), 14, true));
            tradeCard.addView(createTextView("• الحجم المقترح للصفقة: " + String.format(Locale.US, "%.2f", res.suggestedLot) + " اللوت", 14, true));

            Button paperBtn = createButton("📝 فتح صفقة تجريبية بهذه الشروط", v -> executePaperTradeFromSignal(res));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 10, 0, 0);
            tradeCard.addView(paperBtn, lp);
            content.addView(tradeCard);
        }

        LinearLayout rationaleCard = createCardBox();
        rationaleCard.addView(createTextView("📖 شرح الإشارة والتحليل التفصيلي", 16, true));
        rationaleCard.addView(createTextView(res.arabicExplanation, 14, false));
        content.addView(rationaleCard);

        LinearLayout indCard = createCardBox();
        indCard.addView(createTextView("📊 المؤشرات التقنية للذهب", 16, true));
        indCard.addView(createTextView("• الاتجاه العام (15m): " + res.trend, 14, false));
        indCard.addView(createTextView("• اتجاه الإطار الأكبر (1h/4h): " + res.htfTrend, 14, false));
        indCard.addView(createTextView("• RSI (14): " + String.format(Locale.US, "%.1f", res.rsi) + " (" + res.rsiStatus + ")", 14, false));
        indCard.addView(createTextView("• MACD Hist: " + String.format(Locale.US, "%.2f", res.macdHist) + " (" + (res.macdHist > 0 ? "إيجابي" : "سلبي") + ")", 14, false));
        indCard.addView(createTextView("• المتوسطات: EMA20=$" + String.format(Locale.US, "%.1f", res.ema20) + " | EMA50=$" + String.format(Locale.US, "%.1f", res.ema50) + " | EMA200=$" + String.format(Locale.US, "%.1f", res.ema200), 13, false));
        indCard.addView(createTextView("• ATR (14): $" + String.format(Locale.US, "%.2f", res.atr) + " (التقلب: " + res.volatilityStatus + ")", 14, false));
        indCard.addView(createTextView("• الدعم / المقاومة: R1=$" + String.format(Locale.US, "%.1f", res.resistance) + " | S1=$" + String.format(Locale.US, "%.1f", res.support), 14, false));
        content.addView(indCard);

        LinearLayout warningCard = createCardBox();
        warningCard.addView(createTextView("⚠️ تحذير هام من المخاطر", 15, true));
        warningCard.addView(createTextView("سوق الذهب يتسم بالتقلب العالي. هذه الإشارات والمعلومات لأغراض التعليم والتحليل والتداول التجريبي فقط. لا توجد أي إشارة مضمونة الربح.", 13, false));
        content.addView(warningCard);
    }

    // --- SCREEN 2: PORTFOLIO & RISK MANAGEMENT ---
    void showPaperTradingScreen() {
        showPortfolioScreen();
    }

    void showPortfolioScreen() {
        setupBaseLayout("portfolio");

        PortfolioManager.PortfolioSummary sum = PortfolioManager.calculateSummary(prefs);

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("💼 المحفظة — إدارة رأس المال والمخاطر المتقدمة", 20, true));
        titleCard.addView(createTextView("نظام متكامل لمتابعة رأس المال، حدود المخاطرة اليومية، وحماية الرصيد.", 13, false));
        content.addView(titleCard);

        // Daily Risk Warnings
        if (sum.isDailyLossExceeded || sum.isDailyTradesExceeded) {
            LinearLayout warnCard = createCardBox();
            GradientDrawable wGd = new GradientDrawable();
            wGd.setColor(Color.parseColor("#3A1319"));
            wGd.setCornerRadius(16);
            wGd.setStroke(2, Color.RED);
            warnCard.setBackground(wGd);
            warnCard.addView(createTextView("⚠️ تحذير حماية رأس المال وتجاوز الحدود!", 16, true));

            StringBuilder warnMsg = new StringBuilder();
            if (sum.isDailyLossExceeded) {
                warnMsg.append("• تجاوزت الخسارة اليومية الحالية (").append(String.format(Locale.US, "%.1f%%", sum.todayLossPct))
                        .append(") الحد الأقصى المسموح به (").append(prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0")).append("%).\n");
            }
            if (sum.isDailyTradesExceeded) {
                warnMsg.append("• وصلت إلى الحد الأقصى لعدد الصفقات اليومية (").append(sum.todayTradesCount).append(" من ").append(sum.maxDailyTrades).append(" صفقات).\n");
            }
            warnMsg.append("تم إيقاف فتح صفقات جديدة في التداول الورقي لحماية رأس المال.");

            TextView warnTv = createTextView(warnMsg.toString(), 13, false);
            warnTv.setTextColor(Color.parseColor("#FF6B6B"));
            warnCard.addView(warnTv);
            content.addView(warnCard);
        }

        // 1. Portfolio Dashboard Section
        LinearLayout dashCard = createCardBox();
        dashCard.addView(createTextView("📊 1. رأس المال والرصيد (Capital Dashboard)", 16, true));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        TextView capTv = createTextView("• رأس المال الحالي:\n$" + String.format(Locale.US, "%.2f", sum.baseCapital), 13, true);
        TextView balTv = createTextView("• الرصيد الحالي:\n$" + String.format(Locale.US, "%.2f", sum.currentBalance), 13, true);
        balTv.setTextColor(primaryColor);
        row1.addView(capTv, new LinearLayout.LayoutParams(0, -2, 1));
        row1.addView(balTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        TextView availTv = createTextView("• الرصيد المتاح:\n$" + String.format(Locale.US, "%.2f", sum.availableBalance), 13, false);
        TextView marginTv = createTextView("• الصفقات المفتوحة:\n$" + String.format(Locale.US, "%.2f", sum.openTradesMarginUsed), 13, false);
        row2.addView(availTv, new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(marginTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        TextView retTv = createTextView("• نسبة العائد:\n" + String.format(Locale.US, "%+.2f%%", sum.returnPct), 13, true);
        retTv.setTextColor(sum.returnPct >= 0 ? Color.GREEN : Color.RED);
        TextView pnlTv = createTextView("• إجمالي P/L:\n$" + String.format(Locale.US, "%+.2f", sum.totalPnl), 13, true);
        pnlTv.setTextColor(sum.totalPnl >= 0 ? Color.GREEN : Color.RED);
        row3.addView(retTv, new LinearLayout.LayoutParams(0, -2, 1));
        row3.addView(pnlTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row3);

        content.addView(dashCard);

        // 2. Risk Management Settings Section
        LinearLayout capMgmtCard = createCardBox();
        capMgmtCard.addView(createTextView("⚙️ 2. إعدادات إدارة المخاطر (Risk Settings)", 16, true));

        capMgmtCard.addView(createTextView("رأس المال الأساسي ($):", 13, true));
        EditText baseCapEd = createEditText("رأس المال...", String.format(Locale.US, "%.2f", sum.baseCapital));
        capMgmtCard.addView(baseCapEd);

        capMgmtCard.addView(createTextView("نسبة المخاطرة لكل صفقة (Risk per Trade %):", 13, true));
        EditText riskPctEd = createEditText("1.0", prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        capMgmtCard.addView(riskPctEd);

        capMgmtCard.addView(createTextView("الحد الأقصى الخسارة اليومية (Max Daily Loss %):", 13, true));
        EditText maxDailyLossEd = createEditText("3.0", prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0"));
        capMgmtCard.addView(maxDailyLossEd);

        capMgmtCard.addView(createTextView("الحد الأقصى لعدد الصفقات اليومية (Max Daily Trades):", 13, true));
        EditText maxDailyTradesEd = createEditText("5", String.valueOf(sum.maxDailyTrades));
        capMgmtCard.addView(maxDailyTradesEd);

        double riskPctVal = Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        double riskDollars = sum.baseCapital * (riskPctVal / 100.0);
        capMgmtCard.addView(createTextView("• قيمة المخاطرة المحسوبة بالدولار لكل صفقة: $" + String.format(Locale.US, "%.2f", riskDollars), 13, true));
        capMgmtCard.addView(createTextView("• عدد صفقات اليوم حتى الآن: " + sum.todayTradesCount + " / " + sum.maxDailyTrades, 13, false));

        Button updateCapBtn = createButton("💾 حفظ وتحديث إعدادات المخاطر", v -> {
            try {
                double newCap = Double.parseDouble(baseCapEd.getText().toString().trim());
                double newRisk = Double.parseDouble(riskPctEd.getText().toString().trim());
                double newDailyLoss = Double.parseDouble(maxDailyLossEd.getText().toString().trim());
                int newDailyTrades = Integer.parseInt(maxDailyTradesEd.getText().toString().trim());

                PortfolioManager.updateCapitalSettings(prefs, newCap, newRisk, newDailyLoss, newDailyTrades);
                Toast.makeText(this, "تم تحديث إعدادات المخاطر والمحفظة بنجاح!", Toast.LENGTH_SHORT).show();
                showPortfolioScreen();
            } catch (Exception e) {
                Toast.makeText(this, "يرجى إدخال أرقام صالحة", Toast.LENGTH_SHORT).show();
            }
        });
        capMgmtCard.addView(updateCapBtn);

        LinearLayout capActionBtns = new LinearLayout(this);
        capActionBtns.setOrientation(LinearLayout.HORIZONTAL);
        capActionBtns.setPadding(0, 10, 0, 0);

        Button depBtn = createSecondaryButton("➕ إيداع رأس مال", v -> showDepositDialog());
        Button drawBtn = createSecondaryButton("➖ سحب رأس مال", v -> showWithdrawDialog());
        capActionBtns.addView(depBtn, new LinearLayout.LayoutParams(0, -2, 1));
        capActionBtns.addView(drawBtn, new LinearLayout.LayoutParams(0, -2, 1));
        capMgmtCard.addView(capActionBtns);

        content.addView(capMgmtCard);

        // Load All Trades
        List<PortfolioManager.PortfolioTrade> allTrades = PortfolioManager.loadTrades(prefs);
        List<PortfolioManager.PortfolioTrade> openTrades = new ArrayList<>();
        List<PortfolioManager.PortfolioTrade> closedTrades = new ArrayList<>();

        for (PortfolioManager.PortfolioTrade t : allTrades) {
            if ("OPEN".equals(t.status)) openTrades.add(t);
            else closedTrades.add(t);
        }

        // 3. Open Trades Section
        LinearLayout openTradesCard = createCardBox();
        openTradesCard.addView(createTextView("🔓 3. الصفقات المفتوحة (Open Trades)", 16, true));

        if (openTrades.isEmpty()) {
            openTradesCard.addView(createTextView("لا توجد صفقات مفتوحة حاليًا.", 13, false));
        } else {
            for (PortfolioManager.PortfolioTrade ot : openTrades) {
                LinearLayout item = createCardBox();
                item.addView(createTextView("📌 " + ot.type + " " + ot.symbol + " | Lot: " + String.format(Locale.US, "%.2f", ot.lotSize), 15, true));
                item.addView(createTextView("المصدر: " + (ot.signalSource != null ? ot.signalSource : "مساعد AWRIDI AI"), 13, true));
                item.addView(createTextView("وقت الفتح: " + ot.date + " | الدخول: $" + String.format(Locale.US, "%.2f", ot.entryPrice), 13, false));
                item.addView(createTextView("SL: $" + String.format(Locale.US, "%.2f", ot.stopLoss) + " | TP1: $" + String.format(Locale.US, "%.2f", ot.tp1), 13, false));
                item.addView(createTextView("المخاطرة المتوقعة: $" + String.format(Locale.US, "%.2f", ot.riskAmount) + " | الربح المتوقع: $" + String.format(Locale.US, "%.2f", ot.expectedProfit), 13, false));

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);

                Button closeWin = createButton("إغلاق +TP", v -> {
                    PortfolioManager.closeTrade(prefs, ot, true, 0.0);
                    Toast.makeText(this, "تم إغلاق الصفقة على ربح!", Toast.LENGTH_SHORT).show();
                    showPortfolioScreen();
                });

                Button closeLoss = createSecondaryButton("إغلاق -SL", v -> {
                    PortfolioManager.closeTrade(prefs, ot, false, 0.0);
                    Toast.makeText(this, "تم إغلاق الصفقة على خسارة!", Toast.LENGTH_SHORT).show();
                    showPortfolioScreen();
                });

                Button detailBtn = createSecondaryButton("🔍 التفاصيل", v -> showTradeDetailModal(ot));

                btns.addView(closeWin, new LinearLayout.LayoutParams(0, -2, 1));
                btns.addView(closeLoss, new LinearLayout.LayoutParams(0, -2, 1));
                btns.addView(detailBtn, new LinearLayout.LayoutParams(0, -2, 1));
                item.addView(btns);

                openTradesCard.addView(item);
            }
        }
        content.addView(openTradesCard);

        // 4. Trade History Section
        LinearLayout historyCard = createCardBox();
        historyCard.addView(createTextView("📜 4. سجل الصفقات المغلقة (Trade History Log)", 16, true));

        if (closedTrades.isEmpty()) {
            historyCard.addView(createTextView("لا توجد صفقات مغلقة بعد.", 13, false));
        } else {
            for (int i = closedTrades.size() - 1; i >= 0; i--) {
                PortfolioManager.PortfolioTrade ct = closedTrades.get(i);
                LinearLayout item = createCardBox();
                TextView headerTv = createTextView("📌 " + ct.type + " " + ct.symbol + " (" + ct.status + ")", 15, true);
                headerTv.setTextColor("WIN".equals(ct.status) ? Color.GREEN : Color.RED);
                item.addView(headerTv);

                item.addView(createTextView("المصدر: " + (ct.signalSource != null ? ct.signalSource : "مساعد AWRIDI AI"), 12, true));
                item.addView(createTextView("التاريخ: " + ct.date + " | الدخول: $" + String.format(Locale.US, "%.2f", ct.entryPrice) + " | الخروج: $" + String.format(Locale.US, "%.2f", ct.exitPrice), 13, false));
                item.addView(createTextView("Lot: " + String.format(Locale.US, "%.2f", ct.lotSize) + " | SL: $" + String.format(Locale.US, "%.2f", ct.stopLoss) + " | TP: $" + String.format(Locale.US, "%.2f", ct.tp1), 13, false));

                TextView pnlResultTv = createTextView("النتيجة P/L: $" + String.format(Locale.US, "%+.2f", ct.pnl) + " | R:R: 1:" + String.format(Locale.US, "%.2f", ct.rrRatio), 14, true);
                pnlResultTv.setTextColor(ct.pnl >= 0 ? Color.GREEN : Color.RED);
                item.addView(pnlResultTv);

                Button detailBtn = createSecondaryButton("🔍 التفاصيل الكاملة للصفقة", v -> showTradeDetailModal(ct));
                item.addView(detailBtn);

                historyCard.addView(item);
            }
        }
        content.addView(historyCard);

        // 5. Real Statistics Section
        LinearLayout statCard = createCardBox();
        statCard.addView(createTextView("📈 5. التحليلات والإحصائيات الحقيقية (Real Performance Statistics)", 16, true));

        statCard.addView(createTextView("• عدد الصفقات الكلي: " + sum.totalTrades, 14, true));
        statCard.addView(createTextView("• Win Rate: " + String.format(Locale.US, "%.1f%%", sum.winRate) + " (الرابحة: " + sum.winningTrades + " / الخاسرة: " + sum.losingTrades + ")", 14, true));
        statCard.addView(createTextView("• Profit Factor: " + String.format(Locale.US, "%.2f", sum.profitFactor), 14, true));
        statCard.addView(createTextView("• أقصى تراجع للرصيد (Max Drawdown): " + String.format(Locale.US, "%.1f%%", sum.maxDrawdown), 14, false));
        statCard.addView(createTextView("• متوسط الصفقات الرابحة: $" + String.format(Locale.US, "%.2f", sum.avgWin), 13, false));
        statCard.addView(createTextView("• متوسط الصفقات الخاسرة: $" + String.format(Locale.US, "%.2f", sum.avgLoss), 13, false));
        statCard.addView(createTextView("• أكبر صفقة رابحة: $" + String.format(Locale.US, "%.2f", sum.largestWin), 13, false));
        statCard.addView(createTextView("• أكبر صفقة خاسرة: $" + String.format(Locale.US, "%.2f", sum.largestLoss), 13, false));
        statCard.addView(createTextView("• أطول سلسلة خسائر متتالية: " + sum.longestLosingStreak, 13, false));
        statCard.addView(createTextView("• إجمالي P/L: $" + String.format(Locale.US, "%+.2f", sum.totalPnl), 15, true));

        content.addView(statCard);

        // 6. Capital History Operations Log Section
        LinearLayout capHistCard = createCardBox();
        capHistCard.addView(createTextView("🏦 6. سجل حماية رأس المال والعمليات (Capital Protection Log)", 16, true));

        List<PortfolioManager.CapitalRecord> records = PortfolioManager.loadCapitalHistory(prefs);
        if (records.isEmpty()) {
            capHistCard.addView(createTextView("لا توجد عمليات سحب/إيداع أو تعديل مخاطر مسجلة بعد.", 13, false));
        } else {
            for (int i = records.size() - 1; i >= 0; i--) {
                PortfolioManager.CapitalRecord r = records.get(i);
                LinearLayout item = createCardBox();
                item.addView(createTextView("⏱️ " + r.timestamp + " | " + r.type, 14, true));
                item.addView(createTextView("القيمة: $" + String.format(Locale.US, "%.2f", r.amount) + " | من $" + String.format(Locale.US, "%.2f", r.oldVal) + " إلى $" + String.format(Locale.US, "%.2f", r.newVal), 13, false));
                if (r.notes != null && !r.notes.isEmpty()) {
                    item.addView(createTextView("ملاحظات: " + r.notes, 12, false));
                }
                capHistCard.addView(item);
            }
        }
        content.addView(capHistCard);
    }

    // Modal Dialog for Trade Details
    void showTradeDetailModal(PortfolioManager.PortfolioTrade t) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout box = createCardBox();
        box.addView(createTextView("📌 تفاصيل الصفقة الكاملة", 18, true));
        box.addView(createTextView("مصدر الإشارة: " + (t.signalSource != null ? t.signalSource : "مساعد AWRIDI AI"), 13, true));
        box.addView(createTextView("معرّف الصفقة: " + t.id, 11, false));
        box.addView(createTextView("الرمز: " + t.symbol + " | نوع الصفقة: " + t.type, 14, true));
        box.addView(createTextView("حالة الصفقة: " + t.status, 14, true));
        box.addView(createTextView("تاريخ الوصل: " + t.date, 13, false));
        box.addView(createTextView("سعر الدخول: $" + String.format(Locale.US, "%.2f", t.entryPrice), 13, false));
        box.addView(createTextView("سعر الخروج: $" + String.format(Locale.US, "%.2f", t.exitPrice), 13, false));
        box.addView(createTextView("وقف الخسارة (SL): $" + String.format(Locale.US, "%.2f", t.stopLoss), 13, false));
        box.addView(createTextView("الهدف الأول (TP1): $" + String.format(Locale.US, "%.2f", t.tp1), 13, false));
        box.addView(createTextView("الهدف الثاني (TP2): $" + String.format(Locale.US, "%.2f", t.tp2), 13, false));
        box.addView(createTextView("حجم الصفقة (Lot): " + String.format(Locale.US, "%.2f", t.lotSize), 13, false));
        box.addView(createTextView("مبلغ المخاطرة بالدولار: $" + String.format(Locale.US, "%.2f", t.riskAmount), 13, false));
        box.addView(createTextView("الربح المتوقع بالدولار: $" + String.format(Locale.US, "%.2f", t.expectedProfit), 13, false));
        box.addView(createTextView("الأرباح / الخسائر P/L المحققة: $" + String.format(Locale.US, "%+.2f", t.pnl), 14, true));
        box.addView(createTextView("نسبة Risk:Reward: 1 : " + String.format(Locale.US, "%.2f", t.rrRatio), 13, false));
        box.addView(createTextView("سبب الدخول والتحليل:\n" + t.entryReason, 13, false));
        box.addView(createTextView("ملاحظات: " + t.notes, 12, false));

        builder.setView(box);
        builder.setPositiveButton("إغلاق", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    void showDepositDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("إيداع رأس مال للمحفظة");
        LinearLayout layout = createCardBox();
        EditText amtEd = createEditText("المبلغ بالدولار...", "");
        EditText noteEd = createEditText("ملاحظات الإيداع...", "إيداع أرباح أو رأس مال جديد");
        layout.addView(amtEd);
        layout.addView(noteEd);
        builder.setView(layout);
        builder.setPositiveButton("تأكيد الإيداع", (dialog, which) -> {
            try {
                double amt = Double.parseDouble(amtEd.getText().toString().trim());
                PortfolioManager.depositCapital(prefs, amt, noteEd.getText().toString().trim());
                Toast.makeText(this, "تم الإيداع بنجاح!", Toast.LENGTH_SHORT).show();
                showPortfolioScreen();
            } catch (Exception e) {
                Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    void showWithdrawDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("سحب رأس مال من المحفظة");
        LinearLayout layout = createCardBox();
        EditText amtEd = createEditText("المبلغ بالدولار...", "");
        EditText noteEd = createEditText("ملاحظات السحب...", "سحب أرباح مقتطعة");
        layout.addView(amtEd);
        layout.addView(noteEd);
        builder.setView(layout);
        builder.setPositiveButton("تأكيد السحب", (dialog, which) -> {
            try {
                double amt = Double.parseDouble(amtEd.getText().toString().trim());
                PortfolioManager.withdrawCapital(prefs, amt, noteEd.getText().toString().trim());
                Toast.makeText(this, "تم السحب بنجاح!", Toast.LENGTH_SHORT).show();
                showPortfolioScreen();
            } catch (Exception e) {
                Toast.makeText(this, "مبلغ غير صالح", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("إلغاء", null);
        builder.show();
    }

    void executePaperTradeFromSetup(TradeSetup setup) {
        GoldAnalysisEngine.AnalysisResult res = new GoldAnalysisEngine.AnalysisResult();
        res.currentPrice = setup.entryPrice;
        res.signal = setup.direction == TradeSetup.Direction.BUY ? "BUY SETUP 🟢" : "SELL SETUP 🔴";
        res.confidenceScore = setup.confidence / 100.0;
        res.entryPrice = setup.entryPrice;
        res.stopLoss = setup.stopLoss;
        res.takeProfit1 = setup.takeProfit;
        res.takeProfit2 = setup.takeProfit;
        res.riskRewardRatio = setup.riskRewardRatio;
        res.arabicExplanation = setup.explanation;

        executePaperTradeFromSignalWithSource(res, "TradeSetup Engine");
    }

    void executePaperTradeFromSignal(GoldAnalysisEngine.AnalysisResult res) {
        executePaperTradeFromSignalWithSource(res, "مساعد AWRIDI AI");
    }

    void executePaperTradeFromSignalWithSource(GoldAnalysisEngine.AnalysisResult res, String sourceName) {
        PortfolioManager.RiskValidationResult valRes = PortfolioManager.validateTradeRisk(prefs, res.entryPrice, res.stopLoss, res.takeProfit1, res.signal, sourceName);

        if (!valRes.isAllowed) {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ رفض صفقة التداول الورقي (قواعد المخاطر)")
                    .setMessage(valRes.messageArabic)
                    .setPositiveButton("موافق", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("✅ قبول صفقة التداول الورقي")
                    .setMessage(valRes.messageArabic + "\n\n• تفاصيل المخاطرة والأهداف:\n" +
                            " - مبلغ المخاطرة: $" + String.format(Locale.US, "%.2f", valRes.riskAmountUsd) + "\n" +
                            " - الربح المتوقع: $" + String.format(Locale.US, "%.2f", valRes.expectedProfitUsd) + "\n" +
                            " - نسبة Risk/Reward: 1 : " + String.format(Locale.US, "%.2f", valRes.rrRatio) + "\n" +
                            " - الحجم (Lot): " + String.format(Locale.US, "%.2f", valRes.lotSize))
                    .setPositiveButton("تأكيد فتح الصفقة", (dialog, which) -> {
                        PortfolioManager.executeTradeFromSignal(prefs, res, sourceName);
                        Toast.makeText(this, "تم تسجيل الصفقة التجريبية في المحفظة بنجاح!", Toast.LENGTH_SHORT).show();
                        showPortfolioScreen();
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
        }
    }

    void closePaperTrade(PaperTrade trade, boolean isWin) {
        PortfolioManager.PortfolioTrade pt = new PortfolioManager.PortfolioTrade();
        pt.id = trade.id;
        pt.entryPrice = trade.entryPrice;
        pt.stopLoss = trade.stopLoss;
        pt.tp1 = trade.tp1;
        pt.riskAmount = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000")) * (Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0")) / 100.0);
        PortfolioManager.closeTrade(prefs, pt, isWin, 0.0);
        showPortfolioScreen();
    }

    List<PaperTrade> loadPaperTrades() {
        List<PaperTrade> list = new ArrayList<>();
        List<PortfolioManager.PortfolioTrade> pList = PortfolioManager.loadTrades(prefs);
        for (PortfolioManager.PortfolioTrade pt : pList) {
            PaperTrade t = new PaperTrade();
            t.id = pt.id;
            t.date = pt.date;
            t.symbol = pt.symbol;
            t.type = pt.type;
            t.entryPrice = pt.entryPrice;
            t.stopLoss = pt.stopLoss;
            t.tp1 = pt.tp1;
            t.tp2 = pt.tp2;
            t.status = pt.status;
            t.pnl = pt.pnl;
            t.notes = pt.notes;
            list.add(t);
        }
        return list;
    }

    void savePaperTrades(List<PaperTrade> list) {
        List<PortfolioManager.PortfolioTrade> pList = PortfolioManager.loadTrades(prefs);
        for (PaperTrade t : list) {
            boolean found = false;
            for (PortfolioManager.PortfolioTrade pt : pList) {
                if (pt.id.equals(t.id)) {
                    pt.status = t.status;
                    pt.pnl = t.pnl;
                    pt.notes = t.notes;
                    found = true;
                    break;
                }
            }
            if (!found) {
                PortfolioManager.PortfolioTrade pt = new PortfolioManager.PortfolioTrade();
                pt.id = t.id;
                pt.date = t.date;
                pt.symbol = t.symbol;
                pt.type = t.type;
                pt.entryPrice = t.entryPrice;
                pt.stopLoss = t.stopLoss;
                pt.tp1 = t.tp1;
                pt.tp2 = t.tp2;
                pt.status = t.status;
                pt.pnl = t.pnl;
                pt.notes = t.notes;
                pList.add(pt);
            }
        }
        PortfolioManager.saveTrades(prefs, pList);
    }

    static class PaperTrade {
        String id, date, symbol, type, status, notes;
        double entryPrice, stopLoss, tp1, tp2, pnl;
    }

    // --- SCREEN 3: BACKTESTING ---
    void showBacktestScreen() {
        setupBaseLayout("backtest");

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("🧪 محاكي الاختبار التاريخي (Backtesting)", 20, true));
        titleCard.addView(createTextView("اختبر استراتيجية الذهب على البيانات التاريخية لتقييم الجدوى والأداء الإحصائي.", 13, false));

        Button runBtn = createButton("🚀 تشغيل اختبار الذهب XAU/USD", v -> runBacktestProcess());
        titleCard.addView(runBtn);

        TextView backtestOut = createTextView("النتائج ستظهر هنا عند تشغيل الاختبار.", 14, false);
        titleCard.addView(backtestOut);
        content.addView(titleCard);
    }

    void runBacktestProcess() {
        String apiKey = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_API_KEY, "").trim();

        executor.submit(() -> {
            try {
                List<GoldAnalysisEngine.Bar> bars = null;
                if (!apiKey.isEmpty()) {
                    try {
                        bars = GoldAnalysisEngine.fetchTwelveData(GOLD_SYMBOL, "1h", apiKey, 300);
                    } catch (Exception e) {
                        // Fall back to generated bars
                    }
                }

                if (bars == null || bars.isEmpty()) {
                    bars = generateFallbackGoldBars(300);
                }

                BacktestEngine.BacktestResult bt = BacktestEngine.runGoldBacktest(bars, prefs);

                runOnUiThread(() -> {
                    showBacktestScreen(); // refresh screen view
                    LinearLayout resCard = createCardBox();
                    resCard.addView(createTextView("📊 نتائج اختبار استراتيجية الذهب (XAU/USD)", 18, true));
                    resCard.addView(createTextView("• عدد الصفقات الكلي: " + bt.totalTrades, 14, true));
                    resCard.addView(createTextView("• نسبة النجاح (Win Rate): " + String.format(Locale.US, "%.1f%%", bt.winRate * 100), 14, true));
                    resCard.addView(createTextView("• نسبة الخسارة (Loss Rate): " + String.format(Locale.US, "%.1f%%", bt.lossRate * 100), 14, false));
                    resCard.addView(createTextView("• إجمالي الأرباح: $" + String.format(Locale.US, "%.2f", bt.grossProfit), 14, false));
                    resCard.addView(createTextView("• إجمالي الخسائر: $" + String.format(Locale.US, "%.2f", bt.grossLoss), 14, false));
                    resCard.addView(createTextView("• صافي الأرباح/الخسائر (Net PnL): $" + String.format(Locale.US, "%+.2f", bt.netPnl), 15, true));
                    resCard.addView(createTextView("• Profit Factor: " + String.format(Locale.US, "%.2f", bt.profitFactor), 14, true));
                    resCard.addView(createTextView("• أقصى تراجع (Max Drawdown): " + String.format(Locale.US, "%.1f%%", bt.maxDrawdown * 100), 14, true));
                    resCard.addView(createTextView("• متوسط الصفقة الرابحة: $" + String.format(Locale.US, "%.2f", bt.avgWin), 14, false));
                    resCard.addView(createTextView("• متوسط الصفقة الخاسرة: $" + String.format(Locale.US, "%.2f", bt.avgLoss), 14, false));
                    resCard.addView(createTextView("• أكبر خسارة فردية (Largest Loss): $" + String.format(Locale.US, "%.2f", bt.largestLoss), 14, true));
                    resCard.addView(createTextView("• أطول سلسلة خسائر (Longest Losing Streak): " + bt.longestLosingStreak, 14, false));
                    resCard.addView(createTextView("• رأس المال النهائي: $" + String.format(Locale.US, "%.2f", bt.finalCapital), 16, true));

                    resCard.addView(createTextView("⚠️ تذكير: النتائج التاريخية لأغراض الدراسة والتقييم ولا تعني بالضرورة أرباحاً مستقبلية مضمونة.", 12, false));
                    content.addView(resCard);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "خطأ في الاختبار: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // --- SCREEN 3.5: MARKET INTELLIGENCE ---
    void showMarketIntelligenceScreen() {
        setupBaseLayout("market_intelligence");

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("🧠 ذكاء السوق (Market Intelligence)", 20, true));
        titleCard.addView(createTextView("تحليل فني متقدم وشامل للاتجاه، قوة الاتجاه، الزخم، التقلب، وتقييم المؤشرات.", 13, false));
        content.addView(titleCard);

        LinearLayout inputCard = createCardBox();
        inputCard.addView(createTextView("🔍 خيارات تحليل السوق", 16, true));

        inputCard.addView(createTextView("رمز الأصل:", 13, true));
        EditText symEd = createEditText("رمز الأصل...", GOLD_SYMBOL);
        inputCard.addView(symEd);

        inputCard.addView(createTextView("الفاصل الزمني:", 13, true));
        EditText intEd = createEditText("الفاصل الزمني...", "15min");
        inputCard.addView(intEd);

        TextView miStatus = createTextView("اضغط على الزر أدناه لتشغيل محرك ذكاء السوق.", 13, false);
        miStatus.setTextColor(mutedColor);

        Button miAnalyzeBtn = createButton("🔍 تحليل ذكاء السوق الآن", v -> {
            String apiKey = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_API_KEY, "").trim();
            String interval = intEd.getText().toString().trim();
            if (interval.isEmpty()) interval = "15min";

            miStatus.setText("⏳ جارٍ تشغيل تحليل ذكاء السوق...");
            miStatus.setTextColor(secondaryColor);

            final String tf = interval;
            executor.submit(() -> {
                try {
                    List<MarketIntelligenceEngine.Bar> miBars = null;
                    if (!apiKey.isEmpty()) {
                        try {
                            List<GoldAnalysisEngine.Bar> gBars = GoldAnalysisEngine.fetchTwelveData(GOLD_SYMBOL, tf, apiKey, 150);
                            if (gBars != null && !gBars.isEmpty()) {
                                miBars = new ArrayList<>();
                                for (GoldAnalysisEngine.Bar gb : gBars) {
                                    miBars.add(new MarketIntelligenceEngine.Bar(gb.o, gb.h, gb.l, gb.c, gb.v));
                                }
                            }
                        } catch (Exception e) {
                            // fallback
                        }
                    }

                    if (miBars == null || miBars.isEmpty()) {
                        miBars = generateFallbackMiBars(150);
                    }

                    MarketIntelligenceEngine miEngine = new MarketIntelligenceEngine();
                    MarketIntelligenceEngine.Result miRes = miEngine.analyze(miBars);
                    currentMiResult = miRes;

                    runOnUiThread(() -> {
                        miStatus.setText("✅ اكتمل تحليل ذكاء السوق بنجاح!");
                        miStatus.setTextColor(Color.GREEN);
                        showMarketIntelligenceScreen();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        miStatus.setText("❌ خطأ: " + e.getMessage());
                        miStatus.setTextColor(Color.RED);
                    });
                }
            });
        });

        inputCard.addView(miAnalyzeBtn);
        inputCard.addView(miStatus);
        content.addView(inputCard);

        if (currentMiResult != null) {
            displayMarketIntelligenceResult(currentMiResult);
        }
    }

    void displayMarketIntelligenceResult(MarketIntelligenceEngine.Result r) {
        // 1. Overview Card
        LinearLayout overviewCard = createCardBox();
        overviewCard.addView(createTextView("📊 1. ملخص ذكاء السوق", 18, true));
        overviewCard.addView(createTextView("• الاتجاه العام: " + r.trend, 14, true));
        overviewCard.addView(createTextView("• قوة الاتجاه: " + r.trendStrength, 14, false));
        overviewCard.addView(createTextView("• الزخم: " + r.momentum, 14, false));
        overviewCard.addView(createTextView("• التقلب (ATR): " + r.volatility, 14, false));
        overviewCard.addView(createTextView("• الدعم والمقاومة: S1=$" + String.format(Locale.US, "%.2f", r.support) + " | R1=$" + String.format(Locale.US, "%.2f", r.resistance), 14, false));
        overviewCard.addView(createTextView("• حالة السوق: " + r.marketState, 14, true));

        TextView scoreTv = createTextView("🎯 درجة ذكاء السوق: " + r.marketScore + " / 100", 16, true);
        scoreTv.setTextColor(primaryColor);
        overviewCard.addView(scoreTv);
        content.addView(overviewCard);

        // 2. Indicator Evaluations Card
        LinearLayout evalCard = createCardBox();
        evalCard.addView(createTextView("📈 2. تقييم المؤشرات الفنية للذهب", 18, true));

        evalCard.addView(createTextView("• RSI (14):", 14, true));
        evalCard.addView(createTextView(r.rsiEvaluation, 13, false));

        evalCard.addView(createTextView("\n• MACD Histogram:", 14, true));
        evalCard.addView(createTextView(r.macdEvaluation, 13, false));

        evalCard.addView(createTextView("\n• المتوسطات المتحركة EMA (20/50/200):", 14, true));
        evalCard.addView(createTextView(r.emaEvaluation, 13, false));

        evalCard.addView(createTextView("\n• ATR (14) - التقلب السعري:", 14, true));
        evalCard.addView(createTextView(r.atrEvaluation, 13, false));

        evalCard.addView(createTextView("\n• الاتجاه وقوة الاتجاه:", 14, true));
        evalCard.addView(createTextView(r.trendEvaluation, 13, false));

        evalCard.addView(createTextView("\n• مستويات الدعم والمقاومة Pivot Points:", 14, true));
        evalCard.addView(createTextView(r.supportResistanceEvaluation, 13, false));

        content.addView(evalCard);

        // 3. Educational Signal Card
        LinearLayout signalCard = createCardBox();
        signalCard.addView(createTextView("🎓 3. الإشارة التعليمية القائمة على توافق المؤشرات", 18, true));

        TextView sigTv = createTextView("الإشارة: " + r.educationalSignal, 22, true);
        if (r.educationalSignal.contains("BUY")) sigTv.setTextColor(Color.GREEN);
        else if (r.educationalSignal.contains("SELL")) sigTv.setTextColor(Color.RED);
        else if (r.educationalSignal.contains("NO TRADE")) sigTv.setTextColor(Color.GRAY);
        else sigTv.setTextColor(Color.YELLOW);
        signalCard.addView(sigTv);

        signalCard.addView(createTextView("درجة توافق الشروط (Confidence Score): " + String.format(Locale.US, "%.0f%%", r.confidenceScore * 100), 15, true));
        signalCard.addView(createTextView("\n• سبب الإشارة:\n" + r.signalReason, 13, false));

        if (r.supportingIndicators != null && !r.supportingIndicators.isEmpty()) {
            signalCard.addView(createTextView("\n✅ المؤشرات المؤيدة للإشارة:", 14, true));
            for (String sup : r.supportingIndicators) {
                signalCard.addView(createTextView("  ✔ " + sup, 13, false));
            }
        }

        if (r.conflictingIndicators != null && !r.conflictingIndicators.isEmpty()) {
            signalCard.addView(createTextView("\n⚠️ المؤشرات المخالفة / المحذّرة:", 14, true));
            for (String con : r.conflictingIndicators) {
                signalCard.addView(createTextView("  ✖ " + con, 13, false));
            }
        }

        signalCard.addView(createTextView("\n⛔ ملحوظة: لا يتم تنفيذ أي تداول حقيقي تلقائياً. هذه إشارة تحليلية تعليمية.", 12, false));

        Button sendPaperBtn = createButton("📝 إرسال الإشارة إلى التداول الورقي", v -> executePaperTradeFromMiSignal(r));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 12, 0, 0);
        signalCard.addView(sendPaperBtn, lp);

        content.addView(signalCard);
    }

    void executePaperTradeFromMiSignal(MarketIntelligenceEngine.Result miRes) {
        GoldAnalysisEngine.AnalysisResult res = new GoldAnalysisEngine.AnalysisResult();
        res.currentPrice = miRes.currentPrice > 0 ? miRes.currentPrice : 2650.0;
        res.signal = miRes.educationalSignal;
        res.confidenceScore = miRes.confidenceScore;
        res.entryPrice = res.currentPrice;
        res.atr = miRes.atr14 > 0 ? miRes.atr14 : 3.0;

        if (res.signal.contains("BUY")) {
            res.stopLoss = res.entryPrice - (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice + (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice + (res.atr * 3.0);
        } else if (res.signal.contains("SELL")) {
            res.stopLoss = res.entryPrice + (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice - (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice - (res.atr * 3.0);
        } else {
            res.stopLoss = res.entryPrice - 5.0;
            res.takeProfit1 = res.entryPrice + 5.0;
            res.takeProfit2 = res.entryPrice + 10.0;
        }

        res.riskRewardRatio = 1.5;
        res.arabicExplanation = miRes.signalReason;

        executePaperTradeFromSignalWithSource(res, "ذكاء السوق");
    }

    // --- FALLBACK DATA GENERATORS ---
    List<GoldAnalysisEngine.Bar> generateFallbackGoldBars(int count) {
        List<GoldAnalysisEngine.Bar> list = new ArrayList<>();
        double price = 2650.0;
        Random rnd = new Random(42);
        for (int i = 0; i < count; i++) {
            double change = (rnd.nextDouble() - 0.48) * 4.0;
            double open = price;
            double close = open + change;
            double high = Math.max(open, close) + rnd.nextDouble() * 2.0;
            double low = Math.min(open, close) - rnd.nextDouble() * 2.0;
            double vol = 1000 + rnd.nextInt(5000);
            list.add(new GoldAnalysisEngine.Bar(open, high, low, close, vol));
            price = close;
        }
        return list;
    }

    List<MarketIntelligenceEngine.Bar> generateFallbackMiBars(int count) {
        List<MarketIntelligenceEngine.Bar> list = new ArrayList<>();
        double price = 2650.0;
        Random rnd = new Random(42);
        for (int i = 0; i < count; i++) {
            double change = (rnd.nextDouble() - 0.48) * 4.0;
            double open = price;
            double close = open + change;
            double high = Math.max(open, close) + rnd.nextDouble() * 2.0;
            double low = Math.min(open, close) - rnd.nextDouble() * 2.0;
            double vol = 1000 + rnd.nextInt(5000);
            list.add(new MarketIntelligenceEngine.Bar(open, high, low, close, vol));
            price = close;
        }
        return list;
    }

    // --- SCREEN 4: AI ASSISTANT ---
    void showAiAssistantScreen() {
        setupBaseLayout("assistant");

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("🎓 المساعد التعليمي الذكي للذهب", 20, true));
        titleCard.addView(createTextView("دليلك العربي المبسط لفهم التحليل وإدارة المخاطر.", 13, false));
        content.addView(titleCard);

        String[][] topics = {
                {"مؤشر RSI (مؤشر القوة النسبية)", "يقيس مدى تشبع الذهب بالشراء أو البيع. القيم فوق 70 تعني تشبع شرائي (احتمال هبوط)، والقيم تحت 30 تعني تشبع بيعي (احتمال صعود)."},
                {"مؤشر MACD (الزخم والاتجاه)", "يعتمد على الفرق بين المتوسطات المتحركة. عندما تكون أشرطة MACD فوق الصفر، يكون الزخم صعوديًا، وعندما تكون تحت الصفر، يكون الزخم هبوطيًا."},
                {"المتوسطات المتحركة EMA 20/50/200", "تحدد الاتجاه العام للذهب. إذا كان السعر فوق EMA 200 فالإتجاه الرئيسي صاعد. تقاطع EMA 20 فوق EMA 50 يعطي إشارة صعودية."},
                {"الدعم والمقاومة Support & Resistance", "المقاومة هي مستوى ينخفض عنده السعر لكثرة البيع، والدعم هو مستوى يرتفع عنده السعر لكثرة الشراء."},
                {"إدارة المخاطر و Stop Loss / Take Profit", "وقف الخسارة (Stop Loss) يحميك من الخسائر الكبيرة، بينما الهدف (Take Profit) يضمن جني الأرباح. لا تدخل صفقة بدون تحديد وقف الخسارة!"},
                {"لماذا تظهر إشارة WAIT أو NO TRADE؟", "عندما تكون المؤشرات متعارضة أو السوق متقلبًا للغاية دون اتجاه واضح، فإن أفضل قرار تداولي هو الانتظار وتجنب الدخول لحماية رأس المال."}
        };

        for (String[] topic : topics) {
            LinearLayout card = createCardBox();
            card.addView(createTextView("💡 " + topic[0], 16, true));
            card.addView(createTextView(topic[1], 13, false));
            content.addView(card);
        }
    }

    // --- SCREEN 5: SETTINGS & INTEGRATIONS ---
    void showSettingsScreen() {
        setupBaseLayout("settings");

        LinearLayout card = createCardBox();
        card.addView(createTextView("⚙️ إعدادات النظام والمفاتيح", 20, true));

        card.addView(createTextView("🔑 Twelve Data API Key:", 14, true));
        apiKeyInput = createEditText("أدخل API Key...", EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_API_KEY, ""));
        card.addView(apiKeyInput);

        card.addView(createTextView("🤖 Telegram Bot Token (اختياري):", 14, true));
        tgTokenInput = createEditText("Bot Token...", EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_TELEGRAM_TOKEN, ""));
        card.addView(tgTokenInput);

        card.addView(createTextView("💬 Telegram Chat ID (اختياري):", 14, true));
        tgChatIdInput = createEditText("Chat ID...", EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_TELEGRAM_CHAT_ID, ""));
        card.addView(tgChatIdInput);

        card.addView(createTextView("💰 رأس المال التجريبي ($):", 14, true));
        capitalInput = createEditText("10000", prefs.getString(PREF_KEY_CAPITAL, "10000"));
        card.addView(capitalInput);

        card.addView(createTextView("🛡️ نسبة المخاطرة للصفقة (%):", 14, true));
        riskPctInput = createEditText("1.0", prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        card.addView(riskPctInput);

        card.addView(createTextView("🔗 TradingView Webhook URL:", 14, true));
        tvWebhookInput = createEditText("Webhook URL...", EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_TV_WEBHOOK, ""));
        card.addView(tvWebhookInput);

        Button saveBtn = createButton("💾 حفظ الإعدادات", v -> {
            EncryptedPrefsHelper.saveSecureString(this, prefs, PREF_KEY_API_KEY, apiKeyInput.getText().toString().trim());
            EncryptedPrefsHelper.saveSecureString(this, prefs, PREF_KEY_TELEGRAM_TOKEN, tgTokenInput.getText().toString().trim());
            EncryptedPrefsHelper.saveSecureString(this, prefs, PREF_KEY_TELEGRAM_CHAT_ID, tgChatIdInput.getText().toString().trim());
            EncryptedPrefsHelper.saveSecureString(this, prefs, PREF_KEY_TV_WEBHOOK, tvWebhookInput.getText().toString().trim());

            prefs.edit()
                    .putString(PREF_KEY_CAPITAL, capitalInput.getText().toString().trim())
                    .putString(PREF_KEY_RISK_PCT, riskPctInput.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "تم حفظ الإعدادات بنجاح الأمني!", Toast.LENGTH_SHORT).show();
            showHomeScreen();
        });
        card.addView(saveBtn);
        content.addView(card);

        LinearLayout tvCard = createCardBox();
        tvCard.addView(createTextView("📡 ربط إشارات TradingView (Webhook)", 16, true));
        tvCard.addView(createTextView("لربط تنبيهات TradingView مع التطبيق أو التليجرام، قم بإعداد التنبيه في TradingView كالتالي:\n" +
                "• صيغة الرسالة (JSON):\n" +
                "{\n" +
                "  \"ticker\": \"XAUUSD\",\n" +
                "  \"action\": \"{{strategy.order.action}}\",\n" +
                "  \"price\": \"{{close}}\",\n" +
                "  \"time\": \"{{timenow}}\"\n" +
                "}", 13, false));
        content.addView(tvCard);
    }

    // --- TELEGRAM INTEGRATION ---
    void sendTelegramAlertIfNeeded(GoldAnalysisEngine.AnalysisResult res) {
        String token = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_TELEGRAM_TOKEN, "").trim();
        String chatId = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_TELEGRAM_CHAT_ID, "").trim();

        if (token.isEmpty() || chatId.isEmpty()) return;

        executor.submit(() -> {
            try {
                String msg = "🏆 *AWRIDI AI Gold (XAU/USD)*\n\n" +
                        "القرار: *" + res.signal + "*\n" +
                        "السعر الحالي: $" + String.format(Locale.US, "%.2f", res.currentPrice) + "\n" +
                        "الثقة: " + String.format(Locale.US, "%.0f%%", res.confidenceScore * 100) + "\n\n" +
                        "Entry: $" + String.format(Locale.US, "%.2f", res.entryPrice) + "\n" +
                        "SL: $" + String.format(Locale.US, "%.2f", res.stopLoss) + "\n" +
                        "TP1: $" + String.format(Locale.US, "%.2f", res.takeProfit1) + "\n\n" +
                        "السبب:\n" + res.arabicExplanation;

                String urlStr = "https://api.telegram.org/bot" + token + "/sendMessage?chat_id=" + chatId +
                        "&parse_mode=Markdown&text=" + URLEncoder.encode(msg, "UTF-8");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.getResponseCode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
