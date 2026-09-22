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

    // Current Analysis Result Cache
    AnalysisResult currentAnalysis = null;

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

        String[] tabs = {"الرئيسية", "المحفظة", "Backtest", "المساعد", "الإعدادات"};
        String[] keys = {"home", "portfolio", "backtest", "assistant", "settings"};

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
            case "home": showHomeScreen(); break;
            case "portfolio":
            case "paper": showPortfolioScreen(); break;
            case "backtest": showBacktestScreen(); break;
            case "assistant": showAiAssistantScreen(); break;
            case "settings": showSettingsScreen(); break;
        }
    }

    // --- SCREEN 1: HOME (GOLD XAU/USD ANALYSIS) ---
    void showHomeScreen() {
        setupBaseLayout("home");

        LinearLayout heroCard = createCardBox();
        heroCard.addView(createTextView("👑 مساعد تداول الذهب (XAU/USD)", 20, true));
        heroCard.addView(createTextView("تحليل فني متعدد الأطر + محرك القرار + إدارة المخاطر", 13, false));
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
                // Fetch Multi-Timeframe Candles
                Map<String, List<Bar>> mtfBars = new HashMap<>();
                String[] intervals = {"5min", "15min", "1h", "4h"};
                for (String tf : intervals) {
                    List<Bar> bars = fetchTwelveData(GOLD_SYMBOL, tf, apiKey, 150);
                    if (bars != null && !bars.isEmpty()) {
                        mtfBars.put(tf, bars);
                    }
                }

                if (!mtfBars.containsKey("15min") && !mtfBars.containsKey("1h")) {
                    throw new Exception("تعذر جلب بيانات الذهب من Twelve Data. تأكد من صحة المفتاح والاتصال.");
                }

                AnalysisResult result = analyzeGold(mtfBars);
                currentAnalysis = result;

                runOnUiThread(() -> {
                    statusText.setText("✅ اكتمل التحليل بنجاح!");
                    statusText.setTextColor(Color.GREEN);
                    showHomeScreen(); // Refresh view

                    // Trigger Telegram Notification if enabled
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

    void displayAnalysisResult(AnalysisResult res) {
        // Price & Overview Card
        LinearLayout priceCard = createCardBox();
        priceCard.addView(createTextView("🌟 السعر الحالي للذهب (XAU/USD)", 16, true));
        TextView priceTv = createTextView("$ " + String.format(Locale.US, "%.2f", res.currentPrice), 28, true);
        priceTv.setTextColor(primaryColor);
        priceCard.addView(priceTv);
        priceCard.addView(createTextView("التحديث: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()), 12, false));
        content.addView(priceCard);

        // Signal Card
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

        // Trade & Risk Setup Card
        if (res.signal.contains("SETUP")) {
            LinearLayout tradeCard = createCardBox();
            tradeCard.addView(createTextView("📐 خطة إدارة المخاطر للصفقة", 16, true));
            tradeCard.addView(createTextView("• سعر الدخول (Entry): $" + String.format(Locale.US, "%.2f", res.entryPrice), 14, false));
            tradeCard.addView(createTextView("• وقف الخسارة (Stop Loss): $" + String.format(Locale.US, "%.2f", res.stopLoss), 14, true));
            tradeCard.addView(createTextView("• الهدف الأول (Take Profit 1): $" + String.format(Locale.US, "%.2f", res.takeProfit1), 14, false));
            tradeCard.addView(createTextView("• الهدف الثاني (Take Profit 2): $" + String.format(Locale.US, "%.2f", res.takeProfit2), 14, false));
            tradeCard.addView(createTextView("• نسبة المخاطرة/العائد (R:R): 1 : " + String.format(Locale.US, "%.2f", res.riskRewardRatio), 14, false));
            tradeCard.addView(createTextView("• الحجم المقترح للصفقة: " + String.format(Locale.US, "%.2f", res.suggestedLot) + " اللوت", 14, true));

            Button paperBtn = createButton("📝 فتح صفقة تجريبية بهذه الشروط", v -> executePaperTradeFromSignal(res));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 10, 0, 0);
            tradeCard.addView(paperBtn, lp);
            content.addView(tradeCard);
        }

        // Arabic Rationale & Explanation Card
        LinearLayout rationaleCard = createCardBox();
        rationaleCard.addView(createTextView("📖 شرح الإشارة والتحليل التفصيلي", 16, true));
        rationaleCard.addView(createTextView(res.arabicExplanation, 14, false));
        content.addView(rationaleCard);

        // Technical Indicators Summary Card
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

        // Risk Warning Card
        LinearLayout warningCard = createCardBox();
        warningCard.addView(createTextView("⚠️ تحذير هام من المخاطر", 15, true));
        warningCard.addView(createTextView("سوق الذهب يتسم بالتقلب العالي. هذه الإشارات والمعلومات لأغراض التعليم والتحليل والتداول التجريبي فقط. لا توجد أي إشارة مضمونة الربح.", 13, false));
        content.addView(warningCard);
    }

    // --- SCREEN 2: PORTFOLIO & CAPITAL MANAGEMENT ---
    void showPaperTradingScreen() {
        showPortfolioScreen();
    }

    void showPortfolioScreen() {
        setupBaseLayout("portfolio");

        PortfolioManager.PortfolioSummary sum = PortfolioManager.calculateSummary(prefs);

        // Header Title Card
        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("💼 المحفظة — إدارة رأس المال وسجل الصفقات", 20, true));
        titleCard.addView(createTextView("نظام إدارة محفظة الذهب (XAU/USD)، المخاطر والتحليلات الإحصائية الشاملة.", 13, false));
        content.addView(titleCard);

        // Daily Risk Warning Banner (if daily loss exceeded)
        if (sum.isDailyLossExceeded) {
            LinearLayout warnCard = createCardBox();
            GradientDrawable wGd = new GradientDrawable();
            wGd.setColor(Color.parseColor("#3A1319"));
            wGd.setCornerRadius(16);
            wGd.setStroke(2, Color.RED);
            warnCard.setBackground(wGd);
            warnCard.addView(createTextView("⚠️ تحذير إدارة المخاطر اليومية!", 16, true));
            TextView warnTv = createTextView("لقد تجاوزت الخسارة اليومية الحالية (" + String.format(Locale.US, "%.1f%%", sum.todayLossPct) + ") الحد الأقصى المسموح به (" + prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0") + "%). يُنصح بالتوقف عن التداول اليوم لحماية رأس المال.", 13, false);
            warnTv.setTextColor(Color.parseColor("#FF6B6B"));
            warnCard.addView(warnTv);
            content.addView(warnCard);
        }

        // 1. Portfolio Dashboard Card
        LinearLayout dashCard = createCardBox();
        dashCard.addView(createTextView("📊 1. لوحة المحفظة (Portfolio Dashboard)", 16, true));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        TextView capTv = createTextView("• رأس المال الأساسي:\n$" + String.format(Locale.US, "%.2f", sum.baseCapital), 13, true);
        TextView balTv = createTextView("• الرصيد الحالي:\n$" + String.format(Locale.US, "%.2f", sum.currentBalance), 13, true);
        balTv.setTextColor(primaryColor);
        row1.addView(capTv, new LinearLayout.LayoutParams(0, -2, 1));
        row1.addView(balTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        TextView availTv = createTextView("• الرصيد المتاح:\n$" + String.format(Locale.US, "%.2f", sum.availableBalance), 13, false);
        TextView pnlTv = createTextView("• إجمالي P/L:\n$" + String.format(Locale.US, "%+.2f", sum.totalPnl), 13, true);
        pnlTv.setTextColor(sum.totalPnl >= 0 ? Color.GREEN : Color.RED);
        row2.addView(availTv, new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(pnlTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        TextView retTv = createTextView("• نسبة العائد:\n" + String.format(Locale.US, "%+.2f%%", sum.returnPct), 13, true);
        retTv.setTextColor(sum.returnPct >= 0 ? Color.GREEN : Color.RED);
        TextView tradesCntTv = createTextView("• عدد الصفقات المغلقة:\n" + sum.totalTrades + " (المفتوحة: " + sum.openTradesCount + ")", 13, false);
        row3.addView(retTv, new LinearLayout.LayoutParams(0, -2, 1));
        row3.addView(tradesCntTv, new LinearLayout.LayoutParams(0, -2, 1));
        dashCard.addView(row3);

        content.addView(dashCard);

        // 2. Capital Management Card
        LinearLayout capMgmtCard = createCardBox();
        capMgmtCard.addView(createTextView("⚙️ 2. إدارة رأس المال والمخاطر (Capital Management)", 16, true));

        capMgmtCard.addView(createTextView("رأس المال الأساسي ($):", 13, true));
        EditText baseCapEd = createEditText("رأس المال...", String.format(Locale.US, "%.2f", sum.baseCapital));
        capMgmtCard.addView(baseCapEd);

        capMgmtCard.addView(createTextView("نسبة المخاطرة لكل صفقة (Risk per Trade %):", 13, true));
        EditText riskPctEd = createEditText("1.0", prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        capMgmtCard.addView(riskPctEd);

        capMgmtCard.addView(createTextView("الحد الأقصى الخسارة اليومية (Max Daily Loss %):", 13, true));
        EditText maxDailyLossEd = createEditText("3.0", prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0"));
        capMgmtCard.addView(maxDailyLossEd);

        // Dynamic Lot & Risk Calc Preview
        double riskPctVal = Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        double riskDollars = sum.baseCapital * (riskPctVal / 100.0);
        capMgmtCard.addView(createTextView("• مبلغ المخاطرة المحسوب بالدولار للصفقة: $" + String.format(Locale.US, "%.2f", riskDollars), 13, true));

        Button updateCapBtn = createButton("💾 تحديث إعدادات رأس المال والمخاطر", v -> {
            try {
                double newCap = Double.parseDouble(baseCapEd.getText().toString().trim());
                double newRisk = Double.parseDouble(riskPctEd.getText().toString().trim());
                double newDailyLoss = Double.parseDouble(maxDailyLossEd.getText().toString().trim());

                PortfolioManager.updateCapitalSettings(prefs, newCap, newRisk, newDailyLoss);
                Toast.makeText(this, "تم تحديث إعدادات المحفظة بنجاح!", Toast.LENGTH_SHORT).show();
                showPortfolioScreen();
            } catch (Exception e) {
                Toast.makeText(this, "يرجى إدخال أرقام صالحة", Toast.LENGTH_SHORT).show();
            }
        });
        capMgmtCard.addView(updateCapBtn);

        // Deposit & Withdraw Quick Actions
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
                item.addView(createTextView("وقت الفتح: " + ot.date + " | الدخول: $" + String.format(Locale.US, "%.2f", ot.entryPrice), 13, false));
                item.addView(createTextView("SL: $" + String.format(Locale.US, "%.2f", ot.stopLoss) + " | TP1: $" + String.format(Locale.US, "%.2f", ot.tp1) + " | TP2: $" + String.format(Locale.US, "%.2f", ot.tp2), 13, false));
                item.addView(createTextView("المخاطرة: $" + String.format(Locale.US, "%.2f", ot.riskAmount) + " | R:R: 1:" + String.format(Locale.US, "%.2f", ot.rrRatio), 13, false));

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
        historyCard.addView(createTextView("📜 4. سجل الصفقات المغلقة (Trade History)", 16, true));

        if (closedTrades.isEmpty()) {
            historyCard.addView(createTextView("لا توجد صفقات مغلقة بعد.", 13, false));
        } else {
            for (int i = closedTrades.size() - 1; i >= 0; i--) {
                PortfolioManager.PortfolioTrade ct = closedTrades.get(i);
                LinearLayout item = createCardBox();
                TextView headerTv = createTextView("📌 " + ct.type + " " + ct.symbol + " (" + ct.status + ")", 15, true);
                headerTv.setTextColor("WIN".equals(ct.status) ? Color.GREEN : Color.RED);
                item.addView(headerTv);

                item.addView(createTextView("التاريخ: " + ct.date + " | الدخول: $" + String.format(Locale.US, "%.2f", ct.entryPrice) + " | الخروج: $" + String.format(Locale.US, "%.2f", ct.exitPrice), 13, false));
                item.addView(createTextView("Lot: " + String.format(Locale.US, "%.2f", ct.lotSize) + " | SL: $" + String.format(Locale.US, "%.2f", ct.stopLoss) + " | TP: $" + String.format(Locale.US, "%.2f", ct.tp1), 13, false));

                TextView pnlResultTv = createTextView("P/L: $" + String.format(Locale.US, "%+.2f", ct.pnl) + " | R:R: 1:" + String.format(Locale.US, "%.2f", ct.rrRatio), 14, true);
                pnlResultTv.setTextColor(ct.pnl >= 0 ? Color.GREEN : Color.RED);
                item.addView(pnlResultTv);

                Button detailBtn = createSecondaryButton("🔍 التفاصيل الكاملة للصفقة", v -> showTradeDetailModal(ct));
                item.addView(detailBtn);

                historyCard.addView(item);
            }
        }
        content.addView(historyCard);

        // 5. Statistics Section
        LinearLayout statCard = createCardBox();
        statCard.addView(createTextView("📈 5. التحليلات الإحصائية (Statistics)", 16, true));

        statCard.addView(createTextView("• Win Rate: " + String.format(Locale.US, "%.1f%%", sum.winRate) + " (الرابحة: " + sum.winningTrades + " / الخاسرة: " + sum.losingTrades + ")", 14, true));
        statCard.addView(createTextView("• Profit Factor: " + String.format(Locale.US, "%.2f", sum.profitFactor), 14, true));
        statCard.addView(createTextView("• Max Drawdown: " + String.format(Locale.US, "%.1f%%", sum.maxDrawdown), 14, false));
        statCard.addView(createTextView("• متوسط الصفحات الرابحة: $" + String.format(Locale.US, "%.2f", sum.avgWin), 13, false));
        statCard.addView(createTextView("• متوسط الصفحات الخاسرة: $" + String.format(Locale.US, "%.2f", sum.avgLoss), 13, false));
        statCard.addView(createTextView("• أكبر صفقة رابحة (Largest Win): $" + String.format(Locale.US, "%.2f", sum.largestWin), 13, false));
        statCard.addView(createTextView("• أكبر صفقة خاسرة (Largest Loss): $" + String.format(Locale.US, "%.2f", sum.largestLoss), 13, false));

        content.addView(statCard);

        // 7. Capital History Operations Log Section
        LinearLayout capHistCard = createCardBox();
        capHistCard.addView(createTextView("🏦 7. سجل العمليات والتعديلات على رأس المال (Capital History)", 16, true));

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
        box.addView(createTextView("مبلغ المخاطرة: $" + String.format(Locale.US, "%.2f", t.riskAmount), 13, false));
        box.addView(createTextView("الأرباح / الخسائر P/L: $" + String.format(Locale.US, "%+.2f", t.pnl), 14, true));
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

    void executePaperTradeFromSignal(AnalysisResult res) {
        PortfolioManager.PortfolioSummary sum = PortfolioManager.calculateSummary(prefs);
        if (sum.isDailyLossExceeded) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ تحذير حد المخاطرة اليومية")
                .setMessage("لقد تجاوزت الحد الأقصى للخسارة اليومية المحدد بـ (" + prefs.getString(PortfolioManager.PREF_KEY_MAX_DAILY_LOSS, "3.0") + "%).\nهل أنت متاكد من رغبتك في فتح صفقة جديدة رغم تجاوز الحد اليومي؟")
                .setPositiveButton("فتح الصفقة على كل حال", (dialog, which) -> {
                    PortfolioManager.executeTradeFromSignal(prefs, res);
                    Toast.makeText(this, "تم فتح الصفقة التجريبية في المحفظة!", Toast.LENGTH_SHORT).show();
                    showPortfolioScreen();
                })
                .setNegativeButton("التراجع والحفاظ على رأس المال", null)
                .show();
        } else {
            PortfolioManager.executeTradeFromSignal(prefs, res);
            Toast.makeText(this, "تم فتح الصفقة التجريبية في المحفظة بنجاح!", Toast.LENGTH_SHORT).show();
            showPortfolioScreen();
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
        titleCard.addView(createTextView("اختبر استراتيجية الذهب على البيانات التاريخية لتقييم الجدوى.", 13, false));

        Button runBtn = createButton("🚀 تشغيل اختبار الذهب XAU/USD", v -> runBacktestProcess());
        titleCard.addView(runBtn);

        TextView backtestOut = createTextView("النتائج ستظهر هنا عند تشغيل الاختبار.", 14, false);
        titleCard.addView(backtestOut);
        content.addView(titleCard);
    }

    void runBacktestProcess() {
        String apiKey = EncryptedPrefsHelper.getSecureString(prefs, PREF_KEY_API_KEY, "").trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "أدخل مفتاح Twelve Data من شاشة الإعدادات أولًا.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.submit(() -> {
            try {
                List<Bar> bars = fetchTwelveData(GOLD_SYMBOL, "1h", apiKey, 300);
                BacktestResult bt = runGoldBacktest(bars);

                runOnUiThread(() -> {
                    showBacktestScreen(); // clear view
                    LinearLayout resCard = createCardBox();
                    resCard.addView(createTextView("📊 نتائج اختبار استراتيجية الذهب XAU/USD", 18, true));
                    resCard.addView(createTextView("• عدد الصفقات الكلي: " + bt.totalTrades, 14, false));
                    resCard.addView(createTextView("• نسبة الصفقات الرابحة: " + String.format(Locale.US, "%.1f%%", bt.winRate * 100), 14, true));
                    resCard.addView(createTextView("• نسبة الصفقات الخاسرة: " + String.format(Locale.US, "%.1f%%", bt.lossRate * 100), 14, false));
                    resCard.addView(createTextView("• إجمالي الأرباح: $" + String.format(Locale.US, "%.2f", bt.grossProfit), 14, false));
                    resCard.addView(createTextView("• إجمالي الخسائر: $" + String.format(Locale.US, "%.2f", bt.grossLoss), 14, false));
                    resCard.addView(createTextView("• Profit Factor: " + String.format(Locale.US, "%.2f", bt.profitFactor), 14, true));
                    resCard.addView(createTextView("• أقصى تراجع (Max Drawdown): " + String.format(Locale.US, "%.1f%%", bt.maxDrawdown * 100), 14, false));
                    resCard.addView(createTextView("• متوسط الصفحات الرابحة: $" + String.format(Locale.US, "%.2f", bt.avgWin), 14, false));
                    resCard.addView(createTextView("• متوسط الصفحات الخاسرة: $" + String.format(Locale.US, "%.2f", bt.avgLoss), 14, false));
                    resCard.addView(createTextView("• أطول سلسلة خسائر متتالية: " + bt.longestLosingStreak, 14, false));
                    resCard.addView(createTextView("• رأس المال النهائي: $" + String.format(Locale.US, "%.2f", bt.finalCapital), 16, true));

                    resCard.addView(createTextView("⚠️ تذكير: النتائج التاريخية هي لأغراض الدراسة ولا تعني بالضرورة تحقيق نفس الأرباح مستقبلًا.", 12, false));
                    content.addView(resCard);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "خطأ في الاختبار: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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

        // TradingView Integration Help Card
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

    // --- TWELVE DATA API ENGINE ---
    static class Bar {
        double o, h, l, c, v;
        Bar(double o, double h, double l, double c, double v) {
            this.o = o; this.h = h; this.l = l; this.c = c; this.v = v;
        }
    }

    List<Bar> fetchTwelveData(String sym, String interval, String apiKey, int count) throws Exception {
        String urlStr = "https://api.twelvedata.com/time_series?symbol=" + URLEncoder.encode(sym, "UTF-8")
                + "&interval=" + interval + "&outputsize=" + count + "&apikey=" + URLEncoder.encode(apiKey, "UTF-8");

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);

        if (conn.getResponseCode() != 200) {
            throw new Exception("استجابة غير صالحة من السيرفر: " + conn.getResponseCode());
        }

        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();

        JSONObject json = new JSONObject(sb.toString());
        if (!json.has("values")) {
            throw new Exception(json.optString("message", "لا تتوفر بيانات للرمز المطلوب"));
        }

        JSONArray arr = json.getJSONArray("values");
        List<Bar> bars = new ArrayList<>();
        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject obj = arr.getJSONObject(i);
            bars.add(new Bar(
                    obj.getDouble("open"),
                    obj.getDouble("high"),
                    obj.getDouble("low"),
                    obj.getDouble("close"),
                    obj.optDouble("volume", 0)
            ));
        }
        return bars;
    }

    // --- TECHNICAL INDICATORS & ANALYSIS ENGINE ---
    static class AnalysisResult {
        double currentPrice;
        String signal; // BUY SETUP, SELL SETUP, WAIT, NO TRADE
        double confidenceScore;
        double entryPrice, stopLoss, takeProfit1, takeProfit2, riskRewardRatio, suggestedLot;
        String trend;
        String htfTrend;
        double rsi;
        String rsiStatus;
        double macdHist;
        double ema20, ema50, ema200;
        double atr;
        String volatilityStatus;
        double support, resistance;
        String arabicExplanation;
    }

    AnalysisResult analyzeGold(Map<String, List<Bar>> mtfBars) {
        AnalysisResult res = new AnalysisResult();
        List<Bar> bars15m = mtfBars.get("15min");
        if (bars15m == null || bars15m.isEmpty()) bars15m = mtfBars.values().iterator().next();

        int n = bars15m.size();
        Bar latest = bars15m.get(n - 1);
        res.currentPrice = latest.c;

        // Calculate Indicators on 15m
        res.rsi = calcRSI(bars15m, 14, n - 1);
        res.macdHist = calcMACDHist(bars15m, n - 1);
        res.ema20 = calcEMA(bars15m, 20, n - 1);
        res.ema50 = calcEMA(bars15m, 50, n - 1);
        res.ema200 = calcEMA(bars15m, 200, n - 1);
        res.atr = calcATR(bars15m, 14, n - 1);

        // Evaluate Higher Timeframe (1h/4h) Trend
        res.htfTrend = "متوافق";
        List<Bar> bars1h = mtfBars.get("1h");
        if (bars1h != null && !bars1h.isEmpty()) {
            double htfEma50 = calcEMA(bars1h, 50, bars1h.size() - 1);
            Bar last1h = bars1h.get(bars1h.size() - 1);
            if (last1h.c > htfEma50) res.htfTrend = "صاعد (1h) 🟢";
            else res.htfTrend = "هابط (1h) 🔴";
        }

        // RSI Status
        if (res.rsi >= 70) res.rsiStatus = "تشبع شرائي Overbought";
        else if (res.rsi <= 30) res.rsiStatus = "تشبع بيعي Oversold";
        else res.rsiStatus = "متوازن Neutral";

        // Support and Resistance Pivot Points
        double pivot = (latest.h + latest.l + latest.c) / 3.0;
        res.resistance = (2 * pivot) - latest.l;
        res.support = (2 * pivot) - latest.h;

        // Trend Determination
        if (res.currentPrice > res.ema200 && res.ema20 > res.ema50) {
            res.trend = "صاعد قوي 🟢";
        } else if (res.currentPrice < res.ema200 && res.ema20 < res.ema50) {
            res.trend = "هابط قوي 🔴";
        } else {
            res.trend = "عرضي / غير محدد 🟡";
        }

        // Volatility
        res.volatilityStatus = res.atr > 4.0 ? "مرتفع جدًا" : res.atr > 2.0 ? "متوسط" : "منخفض";

        // Multi-Condition Signal Decision Engine
        boolean buyCondition = res.currentPrice > res.ema50 && res.rsi >= 45 && res.rsi <= 68 && res.macdHist > 0 && res.ema20 > res.ema50;
        boolean sellCondition = res.currentPrice < res.ema50 && res.rsi <= 55 && res.rsi >= 32 && res.macdHist < 0 && res.ema20 < res.ema50;

        if (buyCondition && !sellCondition) {
            res.signal = "BUY SETUP 🟢";
            res.confidenceScore = 0.85;
            res.entryPrice = res.currentPrice;
            res.stopLoss = res.entryPrice - (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice + (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice + (res.atr * 3.0);
        } else if (sellCondition && !buyCondition) {
            res.signal = "SELL SETUP 🔴";
            res.confidenceScore = 0.85;
            res.entryPrice = res.currentPrice;
            res.stopLoss = res.entryPrice + (res.atr * 1.5);
            res.takeProfit1 = res.entryPrice - (res.atr * 1.5);
            res.takeProfit2 = res.entryPrice - (res.atr * 3.0);
        } else if (Math.abs(res.rsi - 50) < 5 || res.volatilityStatus.equals("مرتفع جدًا")) {
            res.signal = "NO TRADE 🚫";
            res.confidenceScore = 0.30;
            res.entryPrice = res.currentPrice;
            res.stopLoss = 0; res.takeProfit1 = 0; res.takeProfit2 = 0;
        } else {
            res.signal = "WAIT ⏳";
            res.confidenceScore = 0.50;
            res.entryPrice = res.currentPrice;
            res.stopLoss = 0; res.takeProfit1 = 0; res.takeProfit2 = 0;
        }

        // Calculate Risk / Position Size
        if (res.stopLoss > 0) {
            double riskDiff = Math.abs(res.entryPrice - res.stopLoss);
            res.riskRewardRatio = Math.abs(res.takeProfit1 - res.entryPrice) / Math.max(0.1, riskDiff);

            double capital = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
            double riskPct = Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
            double maxRiskUsd = capital * (riskPct / 100.0);
            res.suggestedLot = maxRiskUsd / (riskDiff * 100.0); // 1 Lot XAU = $100 per $1 move
        }

        // Generate Arabic Rationale
        res.arabicExplanation = generateArabicRationale(res);

        return res;
    }

    String generateArabicRationale(AnalysisResult res) {
        StringBuilder sb = new StringBuilder();
        sb.append("• الاتجاه الحالي (15m): ").append(res.trend).append("\n");
        sb.append("• اتجاه الإطار الأكبر (1h): ").append(res.htfTrend).append("\n");

        if (res.signal.contains("BUY")) {
            sb.append("• السبب: السعر فوق EMA50 وزخم MACD إيجابي مع استقرار RSI عند ").append(String.format(Locale.US, "%.1f", res.rsi)).append(".\n");
            sb.append("• المؤشرات المؤيدة: EMA20 أعلى من EMA50 ، شريط MACD موجب.\n");
            sb.append("• النصحية: دخول شراء مع الالتزام التام بوقف الخسارة عند $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append(".");
        } else if (res.signal.contains("SELL")) {
            sb.append("• السبب: السعر أسفل EMA50 وزخم MACD سلبي مع استقرار RSI عند ").append(String.format(Locale.US, "%.1f", res.rsi)).append(".\n");
            sb.append("• المؤشرات المؤيدة: EMA20 أسفل EMA50 ، شريط MACD سالب.\n");
            sb.append("• النصحية: دخول بيع مع الالتزام بوقف الخسارة عند $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append(".");
        } else if (res.signal.contains("NO TRADE")) {
            sb.append("• السبب: تقلب حاد في الأسواق أو تعادل القوى بين الشراء والبيع.\n");
            sb.append("• النصحية: يُمنع التداول حاليًا للحفاظ على رأس المال ومنع المخاطرة في ظروف غير مواتية.");
        } else {
            sb.append("• السبب: عدم اكتمال شروط الاستراتيجية (تداخل المتوسطات أو RSI متحيّد).\n");
            sb.append("• النصحية: يُفضل الانتظار حتى تتضح إشارة التداول القادمة بشكل أدق.");
        }
        return sb.toString();
    }

    // Mathematical Indicator Helpers
    double calcRSI(List<Bar> bars, int period, int end) {
        if (end < period) return 50.0;
        double gain = 0, loss = 0;
        for (int i = end - period + 1; i <= end; i++) {
            double diff = bars.get(i).c - bars.get(i - 1).c;
            if (diff >= 0) gain += diff;
            else loss -= diff;
        }
        if (loss == 0) return 100.0;
        double rs = gain / loss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    double calcEMA(List<Bar> bars, int period, int end) {
        if (end < 0) return 0;
        int start = Math.max(0, end - (period * 3));
        double k = 2.0 / (period + 1);
        double ema = bars.get(start).c;
        for (int i = start + 1; i <= end; i++) {
            ema = (bars.get(i).c * k) + (ema * (1 - k));
        }
        return ema;
    }

    double calcATR(List<Bar> bars, int period, int end) {
        if (end < 1) return 1.0;
        int start = Math.max(1, end - period + 1);
        double trSum = 0;
        for (int i = start; i <= end; i++) {
            Bar cur = bars.get(i);
            double prevClose = bars.get(i - 1).c;
            double tr = Math.max(cur.h - cur.l, Math.max(Math.abs(cur.h - prevClose), Math.abs(cur.l - prevClose)));
            trSum += tr;
        }
        return trSum / Math.max(1, end - start + 1);
    }

    double calcMACDHist(List<Bar> bars, int end) {
        if (end < 26) return 0.0;
        List<Double> macdSeries = new ArrayList<>();
        int startIdx = Math.max(26, end - 30);
        for (int i = startIdx; i <= end; i++) {
            double ema12 = calcEMA(bars, 12, i);
            double ema26 = calcEMA(bars, 26, i);
            macdSeries.add(ema12 - ema26);
        }

        double macdLine = macdSeries.get(macdSeries.size() - 1);

        // Calculate 9-period EMA of MACD series as Signal Line
        double k = 2.0 / (9 + 1);
        double signalLine = macdSeries.get(0);
        for (int i = 1; i < macdSeries.size(); i++) {
            signalLine = (macdSeries.get(i) * k) + (signalLine * (1 - k));
        }
        return macdLine - signalLine;
    }

    // --- BACKTESTING ENGINE ---
    static class BacktestResult {
        int totalTrades;
        double winRate, lossRate;
        double grossProfit, grossLoss, profitFactor, maxDrawdown;
        double avgWin, avgLoss;
        int longestLosingStreak;
        double finalCapital;
    }

    BacktestResult runGoldBacktest(List<Bar> bars) {
        BacktestResult bt = new BacktestResult();
        double startCap = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
        double cash = startCap, peak = cash;
        int wins = 0, losses = 0;
        int currentLossStreak = 0, maxLossStreak = 0;

        for (int i = 50; i < bars.size() - 1; i++) {
            double rsi = calcRSI(bars, 14, i);
            double ema20 = calcEMA(bars, 20, i);
            double ema50 = calcEMA(bars, 50, i);
            double macdHist = calcMACDHist(bars, i);
            double atr = calcATR(bars, 14, i);
            Bar bar = bars.get(i);

            if (bar.c > ema50 && rsi >= 45 && rsi <= 68 && macdHist > 0 && ema20 > ema50) {
                // Buy Trade Setup
                bt.totalTrades++;
                double entry = bar.c;
                double sl = entry - (atr * 1.5);
                double tp = entry + (atr * 2.0);

                // Track Trade across subsequent bars
                boolean closed = false;
                for (int j = i + 1; j < bars.size(); j++) {
                    Bar futureBar = bars.get(j);
                    if (futureBar.h >= tp) {
                        wins++;
                        double pnl = atr * 2.0 * 10;
                        cash += pnl;
                        bt.grossProfit += pnl;
                        currentLossStreak = 0;
                        closed = true;
                        i = j; // Advance index to trade closure bar
                        break;
                    } else if (futureBar.l <= sl) {
                        losses++;
                        double pnl = atr * 1.5 * 10;
                        cash -= pnl;
                        bt.grossLoss += pnl;
                        currentLossStreak++;
                        if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                        closed = true;
                        i = j; // Advance index to trade closure bar
                        break;
                    }
                }
            }
            peak = Math.max(peak, cash);
            double dd = (peak - cash) / peak;
            if (dd > bt.maxDrawdown) bt.maxDrawdown = dd;
        }

        bt.finalCapital = cash;
        bt.winRate = bt.totalTrades > 0 ? (double) wins / bt.totalTrades : 0;
        bt.lossRate = bt.totalTrades > 0 ? (double) losses / bt.totalTrades : 0;
        bt.profitFactor = bt.grossLoss > 0 ? bt.grossProfit / bt.grossLoss : (bt.grossProfit > 0 ? 99.0 : 0);
        bt.avgWin = wins > 0 ? bt.grossProfit / wins : 0;
        bt.avgLoss = losses > 0 ? bt.grossLoss / losses : 0;
        bt.longestLosingStreak = maxLossStreak;

        return bt;
    }

    // --- TELEGRAM INTEGRATION ---
    void sendTelegramAlertIfNeeded(AnalysisResult res) {
        String token = prefs.getString(PREF_KEY_TELEGRAM_TOKEN, "").trim();
        String chatId = prefs.getString(PREF_KEY_TELEGRAM_CHAT_ID, "").trim();

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
                conn.getResponseCode(); // Execute request
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
