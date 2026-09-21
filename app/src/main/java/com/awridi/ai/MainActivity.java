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

    // Current Analysis Cache & Intelligence Output
    GoldAnalysisEngine.AnalysisResult currentAnalysis = null;
    MarketIntelligenceBot.MarketIntelligenceReport currentIntelligenceReport = null;

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

        String[] tabs = {"الرئيسية", "ذكاء السوق", "التداول التجريبي", "Backtest", "المساعد", "الإعدادات"};
        String[] keys = {"home", "intel", "paper", "backtest", "assistant", "settings"};

        for (int i = 0; i < tabs.length; i++) {
            final String tabKey = keys[i];
            Button b;
            if (tabKey.equals(activeTab)) {
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
            case "intel": showMarketIntelligenceScreen(); break;
            case "paper": showPaperTradingScreen(); break;
            case "backtest": showBacktestScreen(); break;
            case "assistant": showAiAssistantScreen(); break;
            case "settings": showSettingsScreen(); break;
        }
    }

    // --- SCREEN 1: HOME ---
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

    // --- SCREEN 2: MARKET INTELLIGENCE ---
    void showMarketIntelligenceScreen() {
        setupBaseLayout("intel");

        LinearLayout heroCard = createCardBox();
        heroCard.addView(createTextView("🧠 طبقة ذكاء السوق (Market Intelligence)", 20, true));
        heroCard.addView(createTextView("تحليل السلوك التاريخي + التشابه + Walk-Forward + منع Look-Ahead Bias", 13, false));
        content.addView(heroCard);

        if (currentIntelligenceReport != null) {
            displayMarketIntelligenceReport(currentIntelligenceReport);
        } else {
            LinearLayout emptyCard = createCardBox();
            emptyCard.addView(createTextView("قم بتشغيل تحليل الذهب من الشاشة الرئيسية لتحديث نتائج ذكاء السوق.", 14, false));
            content.addView(emptyCard);
        }
    }

    void displayMarketIntelligenceReport(MarketIntelligenceBot.MarketIntelligenceReport r) {
        LinearLayout reportCard = createCardBox();
        reportCard.addView(createTextView("📊 ملخص ذكاء السوق الحاضر والتاريخي", 18, true));
        reportCard.addView(createTextView(r.fullArabicSummary, 14, false));

        // Copy & Share Actions
        Button copyBtn = createSecondaryButton("📋 نسخ التحليل بالكامل", v -> {
            AnalysisShareHelper.copyTextToClipboard(this, r.fullArabicSummary);
        });

        Button shareBtn = createButton("📤 مشاركة النتائج", v -> {
            AnalysisShareHelper.shareText(this, r.fullArabicSummary, "مشاركة نتائج ذكاء السوق XAU/USD");
        });

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(copyBtn, new LinearLayout.LayoutParams(0, -2, 1));
        btns.addView(shareBtn, new LinearLayout.LayoutParams(0, -2, 1));
        reportCard.addView(btns);

        content.addView(reportCard);
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

                // Run Market Intelligence Engine
                currentIntelligenceReport = MarketIntelligenceBot.generateIntelligence(mtfBars, prefs);

                runOnUiThread(() -> {
                    statusText.setText("✅ اكتمل التحليل وبناء ذكاء السوق بنجاح!");
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

    void displayAnalysisResult(GoldAnalysisEngine.AnalysisResult res) {
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

    // --- PAPER TRADING ---
    void showPaperTradingScreen() {
        setupBaseLayout("paper");

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("📝 حساب التداول التجريبي (Paper Trading)", 20, true));
        titleCard.addView(createTextView("اختبر مهاراتك دون المخاطرة بأي أموال حقيقية.", 13, false));
        content.addView(titleCard);

        List<PaperTradingManager.PaperTrade> trades = PaperTradingManager.loadPaperTrades(prefs);
        double initialCap = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
        double totalPnl = 0;
        int winCount = 0;
        int closedCount = 0;

        for (PaperTradingManager.PaperTrade t : trades) {
            if (!t.status.equals("OPEN")) {
                totalPnl += t.pnl;
                closedCount++;
                if (t.pnl > 0) winCount++;
            }
        }

        LinearLayout statCard = createCardBox();
        statCard.addView(createTextView("📊 ملخص الأداء التجريبي", 16, true));
        statCard.addView(createTextView("• رأس المال الأولي: $" + String.format(Locale.US, "%.2f", initialCap), 14, false));
        statCard.addView(createTextView("• الرصيد الحالي: $" + String.format(Locale.US, "%.2f", initialCap + totalPnl), 14, true));
        statCard.addView(createTextView("• إجمالي الربح/الخسارة: $" + String.format(Locale.US, "%.2f", totalPnl), 14, true));
        if (closedCount > 0) {
            double winRate = (double) winCount / closedCount * 100.0;
            statCard.addView(createTextView("• نسبة الصفحات الرابحة: " + String.format(Locale.US, "%.1f%%", winRate), 14, false));
        }
        content.addView(statCard);

        LinearLayout historyCard = createCardBox();
        historyCard.addView(createTextView("📜 سجل الصفقات التجريبية", 16, true));

        if (trades.isEmpty()) {
            historyCard.addView(createTextView("لا توجد صفقات تجريبية مسجلة بعد. يمكنك إضافتها عند إجراء التحليل.", 13, false));
        } else {
            for (int i = trades.size() - 1; i >= 0; i--) {
                final PaperTradingManager.PaperTrade pt = trades.get(i);
                LinearLayout item = createCardBox();
                item.addView(createTextView("📌 " + pt.type + " " + pt.symbol + " (" + pt.status + ")", 15, true));
                item.addView(createTextView("التاريخ: " + pt.date + " | الدخول: $" + String.format(Locale.US, "%.2f", pt.entryPrice), 13, false));
                item.addView(createTextView("SL: $" + String.format(Locale.US, "%.2f", pt.stopLoss) + " | TP1: $" + String.format(Locale.US, "%.2f", pt.tp1), 13, false));
                if (pt.status.equals("OPEN")) {
                    Button closeWin = createButton("إغلاق على ربح (+TP)", v -> closePaperTrade(pt, true));
                    Button closeLoss = createSecondaryButton("إغلاق على خسارة (-SL)", v -> closePaperTrade(pt, false));
                    LinearLayout btns = new LinearLayout(this);
                    btns.setOrientation(LinearLayout.HORIZONTAL);
                    btns.addView(closeWin, new LinearLayout.LayoutParams(0, -2, 1));
                    btns.addView(closeLoss, new LinearLayout.LayoutParams(0, -2, 1));
                    item.addView(btns);
                } else {
                    item.addView(createTextView("النتيجة: " + String.format(Locale.US, "%.2f$", pt.pnl) + " (" + pt.notes + ")", 14, true));
                }
                historyCard.addView(item);
            }
        }
        content.addView(historyCard);
    }

    void executePaperTradeFromSignal(GoldAnalysisEngine.AnalysisResult res) {
        List<PaperTradingManager.PaperTrade> list = PaperTradingManager.loadPaperTrades(prefs);
        PaperTradingManager.PaperTrade t = new PaperTradingManager.PaperTrade();
        t.id = UUID.randomUUID().toString();
        t.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        t.symbol = GOLD_SYMBOL;
        t.type = res.signal.contains("BUY") ? "BUY" : "SELL";
        t.entryPrice = res.entryPrice;
        t.stopLoss = res.stopLoss;
        t.tp1 = res.takeProfit1;
        t.tp2 = res.takeProfit2;
        t.status = "OPEN";
        t.pnl = 0;
        t.notes = "صفقة منفذة بناء على إشارة النظام";

        list.add(t);
        PaperTradingManager.savePaperTrades(prefs, list);

        Toast.makeText(this, "تم إضافة الصفقة التجريبية إلى السجل بنجاح!", Toast.LENGTH_SHORT).show();
        showPaperTradingScreen();
    }

    void closePaperTrade(PaperTradingManager.PaperTrade trade, boolean isWin) {
        List<PaperTradingManager.PaperTrade> list = PaperTradingManager.loadPaperTrades(prefs);
        for (PaperTradingManager.PaperTrade t : list) {
            if (t.id.equals(trade.id)) {
                t.status = isWin ? "WIN" : "LOSS";
                double riskAmount = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000")) * (Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0")) / 100.0);
                t.pnl = isWin ? riskAmount * 1.5 : -riskAmount;
                t.notes = isWin ? "تم ضرب الهدف" : "تم ضرب وقف الخسارة";
                break;
            }
        }
        PaperTradingManager.savePaperTrades(prefs, list);
        showPaperTradingScreen();
    }

    // --- BACKTESTING ---
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
                List<GoldAnalysisEngine.Bar> bars = GoldAnalysisEngine.fetchTwelveData(GOLD_SYMBOL, "1h", apiKey, 300);
                BacktestEngine.BacktestResult bt = BacktestEngine.runGoldBacktest(bars, prefs);

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

    // --- AI ASSISTANT ---
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

    // --- SETTINGS ---
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
    }

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

                TelegramNotifier.sendTelegramMessageAsync(token, chatId, msg, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
