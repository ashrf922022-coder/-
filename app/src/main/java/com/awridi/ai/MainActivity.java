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

        String[] tabs = {"الرئيسية", "التداول التجريبي", "Backtest", "المساعد", "الإعدادات"};
        String[] keys = {"home", "paper", "backtest", "assistant", "settings"};

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
            case "paper": showPaperTradingScreen(); break;
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
        String apiKey = prefs.getString(PREF_KEY_API_KEY, "").trim();
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
        // Actions Card (Copy & Share Buttons)
        LinearLayout actionsCard = createCardBox();
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

        Button copyBtn = createButton("📋 نسخ التحليل بالكامل", v -> copyAnalysisToClipboard(res));
        Button shareBtn = createSecondaryButton("📤 مشاركة النتائج", v -> shareAnalysis(res));

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1);
        lp1.setMargins(0, 0, 8, 0);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1);
        lp2.setMargins(8, 0, 0, 0);

        btnLayout.addView(copyBtn, lp1);
        btnLayout.addView(shareBtn, lp2);
        actionsCard.addView(btnLayout);
        content.addView(actionsCard);

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

    void copyAnalysisToClipboard(AnalysisResult res) {
        if (res == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 *تحليل الذهب AWRIDI AI (XAU/USD)*\n");
        sb.append("----------------------------------\n");
        sb.append("💰 *السعر الحالي:* $").append(String.format(Locale.US, "%.2f", res.currentPrice)).append("\n");
        sb.append("🎯 *قرار النظام:* ").append(res.signal).append("\n");
        sb.append("📊 *نسبة توافق الشروط (الثقة):* ").append(String.format(Locale.US, "%.0f%%", res.confidenceScore * 100)).append("\n\n");

        if (res.signal.contains("SETUP")) {
            sb.append("📐 *خطة إدارة المخاطر:*\n");
            sb.append("• سعر الدخول (Entry): $").append(String.format(Locale.US, "%.2f", res.entryPrice)).append("\n");
            sb.append("• وقف الخسارة (Stop Loss): $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append("\n");
            sb.append("• الهدف الأول (TP1): $").append(String.format(Locale.US, "%.2f", res.takeProfit1)).append("\n");
            sb.append("• الهدف الثاني (TP2): $").append(String.format(Locale.US, "%.2f", res.takeProfit2)).append("\n");
            sb.append("• نسبة المخاطرة/العائد (R:R): 1 : ").append(String.format(Locale.US, "%.2f", res.riskRewardRatio)).append("\n");
            sb.append("• الحجم المقترح للصفقة: ").append(String.format(Locale.US, "%.2f", res.suggestedLot)).append(" اللوت\n\n");
        }

        sb.append("📖 *شرح الإشارة والتحليل التفصيلي:*\n");
        sb.append(res.arabicExplanation).append("\n\n");

        sb.append("📊 *المؤشرات التقنية:*\n");
        sb.append("• الاتجاه العام (15m): ").append(res.trend).append("\n");
        sb.append("• اتجاه الإطار الأكبر (1h/4h): ").append(res.htfTrend).append("\n");
        sb.append("• RSI (14): ").append(String.format(Locale.US, "%.1f", res.rsi)).append(" (").append(res.rsiStatus).append(")\n");
        sb.append("• MACD Hist: ").append(String.format(Locale.US, "%.2f", res.macdHist)).append(" (").append(res.macdHist > 0 ? "إيجابي" : "سلبي").append(")\n");
        sb.append("• المتوسطات: EMA20=$").append(String.format(Locale.US, "%.1f", res.ema20))
          .append(" | EMA50=$").append(String.format(Locale.US, "%.1f", res.ema50))
          .append(" | EMA200=$").append(String.format(Locale.US, "%.1f", res.ema200)).append("\n");
        sb.append("• ATR (14): $").append(String.format(Locale.US, "%.2f", res.atr)).append(" (التقلب: ").append(res.volatilityStatus).append(")\n");
        sb.append("• الدعم والمقاومة: R1=$").append(String.format(Locale.US, "%.1f", res.resistance))
          .append(" | S1=$").append(String.format(Locale.US, "%.1f", res.support)).append("\n\n");

        sb.append("⚠️ *تحذير هام:* سوق الذهب يتسم بالتقلب العالي. هذه البيانات لأغراض التعليم والتحليل والتداول التجريبي فقط. لا توجد أي إشارة مضمونة الربح.");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AWRIDI_Gold_Analysis", sb.toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "تم نسخ التحليل بالكامل ✓", Toast.LENGTH_SHORT).show();
        }
    }

    void shareAnalysis(AnalysisResult res) {
        if (res == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 *تحليل الذهب AWRIDI AI (XAU/USD)*\n");
        sb.append("----------------------------------\n");
        sb.append("💰 *السعر الحالي:* $").append(String.format(Locale.US, "%.2f", res.currentPrice)).append("\n");
        sb.append("🎯 *قرار النظام:* ").append(res.signal).append("\n");
        sb.append("📊 *نسبة توافق الشروط (الثقة):* ").append(String.format(Locale.US, "%.0f%%", res.confidenceScore * 100)).append("\n\n");

        if (res.signal.contains("SETUP")) {
            sb.append("📐 *خطة إدارة المخاطر:*\n");
            sb.append("• سعر الدخول (Entry): $").append(String.format(Locale.US, "%.2f", res.entryPrice)).append("\n");
            sb.append("• وقف الخسارة (Stop Loss): $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append("\n");
            sb.append("• الهدف الأول (TP1): $").append(String.format(Locale.US, "%.2f", res.takeProfit1)).append("\n");
            sb.append("• الهدف الثاني (TP2): $").append(String.format(Locale.US, "%.2f", res.takeProfit2)).append("\n");
            sb.append("• نسبة المخاطرة/العائد (R:R): 1 : ").append(String.format(Locale.US, "%.2f", res.riskRewardRatio)).append("\n");
            sb.append("• الحجم المقترح للصفقة: ").append(String.format(Locale.US, "%.2f", res.suggestedLot)).append(" اللوت\n\n");
        }

        sb.append("📖 *شرح الإشارة والتحليل التفصيلي:*\n");
        sb.append(res.arabicExplanation).append("\n\n");

        sb.append("📊 *المؤشرات التقنية:*\n");
        sb.append("• الاتجاه العام (15m): ").append(res.trend).append("\n");
        sb.append("• اتجاه الإطار الأكبر (1h/4h): ").append(res.htfTrend).append("\n");
        sb.append("• RSI (14): ").append(String.format(Locale.US, "%.1f", res.rsi)).append(" (").append(res.rsiStatus).append(")\n");
        sb.append("• MACD Hist: ").append(String.format(Locale.US, "%.2f", res.macdHist)).append(" (").append(res.macdHist > 0 ? "إيجابي" : "سلبي").append(")\n");
        sb.append("• المتوسطات: EMA20=$").append(String.format(Locale.US, "%.1f", res.ema20))
          .append(" | EMA50=$").append(String.format(Locale.US, "%.1f", res.ema50))
          .append(" | EMA200=$").append(String.format(Locale.US, "%.1f", res.ema200)).append("\n");
        sb.append("• ATR (14): $").append(String.format(Locale.US, "%.2f", res.atr)).append(" (التقلب: ").append(res.volatilityStatus).append(")\n");
        sb.append("• الدعم والمقاومة: R1=$").append(String.format(Locale.US, "%.1f", res.resistance))
          .append(" | S1=$").append(String.format(Locale.US, "%.1f", res.support)).append("\n\n");

        sb.append("⚠️ *تحذير هام:* سوق الذهب يتسم بالتقلب العالي. هذه البيانات لأغراض التعليم والتحليل والتداول التجريبي فقط. لا توجد أي إشارة مضمونة الربح.");

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "مشاركة نتائج تحليل الذهب عبر:");
        startActivity(shareIntent);
    }

    // --- SCREEN 2: PAPER TRADING ---
    void showPaperTradingScreen() {
        setupBaseLayout("paper");

        LinearLayout titleCard = createCardBox();
        titleCard.addView(createTextView("📝 حساب التداول التجريبي (Paper Trading)", 20, true));
        titleCard.addView(createTextView("اختبر مهاراتك دون المخاطرة بأي أموال حقيقية.", 13, false));
        content.addView(titleCard);

        List<PaperTrade> trades = loadPaperTrades();
        double initialCap = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000"));
        double totalPnl = 0;
        int winCount = 0;
        int closedCount = 0;

        for (PaperTrade t : trades) {
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
                final PaperTrade pt = trades.get(i);
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

    void executePaperTradeFromSignal(AnalysisResult res) {
        List<PaperTrade> list = loadPaperTrades();
        PaperTrade t = new PaperTrade();
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
        savePaperTrades(list);

        Toast.makeText(this, "تم إضافة الصفقة التجريبية إلى السجل بنجاح!", Toast.LENGTH_SHORT).show();
        showPaperTradingScreen();
    }

    void closePaperTrade(PaperTrade trade, boolean isWin) {
        List<PaperTrade> list = loadPaperTrades();
        for (PaperTrade t : list) {
            if (t.id.equals(trade.id)) {
                t.status = isWin ? "WIN" : "LOSS";
                double riskAmount = Double.parseDouble(prefs.getString(PREF_KEY_CAPITAL, "10000")) * (Double.parseDouble(prefs.getString(PREF_KEY_RISK_PCT, "1.0")) / 100.0);
                t.pnl = isWin ? riskAmount * 1.5 : -riskAmount;
                t.notes = isWin ? "تم ضرب الهدف" : "تم ضرب وقف الخسارة";
                break;
            }
        }
        savePaperTrades(list);
        showPaperTradingScreen();
    }

    List<PaperTrade> loadPaperTrades() {
        List<PaperTrade> list = new ArrayList<>();
        try {
            String jsonStr = prefs.getString(PREF_KEY_PAPER_TRADES, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                PaperTrade t = new PaperTrade();
                t.id = obj.optString("id");
                t.date = obj.optString("date");
                t.symbol = obj.optString("symbol");
                t.type = obj.optString("type");
                t.entryPrice = obj.optDouble("entryPrice");
                t.stopLoss = obj.optDouble("stopLoss");
                t.tp1 = obj.optDouble("tp1");
                t.tp2 = obj.optDouble("tp2");
                t.status = obj.optString("status");
                t.pnl = obj.optDouble("pnl");
                t.notes = obj.optString("notes");
                list.add(t);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    void savePaperTrades(List<PaperTrade> list) {
        try {
            JSONArray arr = new JSONArray();
            for (PaperTrade t : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("date", t.date);
                obj.put("symbol", t.symbol);
                obj.put("type", t.type);
                obj.put("entryPrice", t.entryPrice);
                obj.put("stopLoss", t.stopLoss);
                obj.put("tp1", t.tp1);
                obj.put("tp2", t.tp2);
                obj.put("status", t.status);
                obj.put("pnl", t.pnl);
                obj.put("notes", t.notes);
                arr.put(obj);
            }
            prefs.edit().putString(PREF_KEY_PAPER_TRADES, arr.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
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
        String apiKey = prefs.getString(PREF_KEY_API_KEY, "").trim();
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
        apiKeyInput = createEditText("أدخل API Key...", prefs.getString(PREF_KEY_API_KEY, ""));
        card.addView(apiKeyInput);

        card.addView(createTextView("🤖 Telegram Bot Token (اختياري):", 14, true));
        tgTokenInput = createEditText("Bot Token...", prefs.getString(PREF_KEY_TELEGRAM_TOKEN, ""));
        card.addView(tgTokenInput);

        card.addView(createTextView("💬 Telegram Chat ID (اختياري):", 14, true));
        tgChatIdInput = createEditText("Chat ID...", prefs.getString(PREF_KEY_TELEGRAM_CHAT_ID, ""));
        card.addView(tgChatIdInput);

        card.addView(createTextView("💰 رأس المال التجريبي ($):", 14, true));
        capitalInput = createEditText("10000", prefs.getString(PREF_KEY_CAPITAL, "10000"));
        card.addView(capitalInput);

        card.addView(createTextView("🛡️ نسبة المخاطرة للصفقة (%):", 14, true));
        riskPctInput = createEditText("1.0", prefs.getString(PREF_KEY_RISK_PCT, "1.0"));
        card.addView(riskPctInput);

        card.addView(createTextView("🔗 TradingView Webhook URL:", 14, true));
        tvWebhookInput = createEditText("Webhook URL...", prefs.getString(PREF_KEY_TV_WEBHOOK, ""));
        card.addView(tvWebhookInput);

        Button saveBtn = createButton("💾 حفظ الإعدادات", v -> {
            prefs.edit()
                .putString(PREF_KEY_API_KEY, apiKeyInput.getText().toString().trim())
                .putString(PREF_KEY_TELEGRAM_TOKEN, tgTokenInput.getText().toString().trim())
                .putString(PREF_KEY_TELEGRAM_CHAT_ID, tgChatIdInput.getText().toString().trim())
                .putString(PREF_KEY_CAPITAL, capitalInput.getText().toString().trim())
                .putString(PREF_KEY_RISK_PCT, riskPctInput.getText().toString().trim())
                .putString(PREF_KEY_TV_WEBHOOK, tvWebhookInput.getText().toString().trim())
                .apply();
            Toast.makeText(this, "تم حفظ الإعدادات بنجاح!", Toast.LENGTH_SHORT).show();
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
