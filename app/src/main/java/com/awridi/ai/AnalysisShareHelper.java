package com.awridi.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.util.Locale;

public class AnalysisShareHelper {

    public static String formatAnalysisToText(GoldAnalysisEngine.AnalysisResult res) {
        if (res == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 *تحليل الذهب AWRIDI AI (XAU/USD)*\n");
        sb.append("----------------------------------\n");
        sb.append("💰 *السعر الحالي:* $").append(String.format(Locale.US, "%.2f", res.currentPrice)).append("\n");
        sb.append("🎯 *قرار النظام:* ").append(res.signal).append("\n");
        sb.append("📊 *نسبة توافق الشروط (الثقة):* ").append(String.format(Locale.US, "%.0f%%", res.confidenceScore * 100)).append("\n\n");

        if (res.signal != null && res.signal.contains("SETUP")) {
            sb.append("📐 *خطة إدارة المخاطر:*\n");
            sb.append("• سعر الدخول (Entry): $").append(String.format(Locale.US, "%.2f", res.entryPrice)).append("\n");
            sb.append("• وقف الخسارة (Stop Loss): $").append(String.format(Locale.US, "%.2f", res.stopLoss)).append("\n");
            sb.append("• الهدف الأول (TP1): $").append(String.format(Locale.US, "%.2f", res.takeProfit1)).append("\n");
            sb.append("• الهدف الثاني (TP2): $").append(String.format(Locale.US, "%.2f", res.takeProfit2)).append("\n");
            sb.append("• نسبة المخاطرة/العائد (R:R): 1 : ").append(String.format(Locale.US, "%.2f", res.riskRewardRatio)).append("\n");
            sb.append("• الحجم المقترح للصفقة: ").append(String.format(Locale.US, "%.2f", res.suggestedLot)).append(" اللوت\n\n");
        }

        sb.append("📖 *شرح الإشارة والتحليل التفصيلي:*\n");
        sb.append(res.arabicExplanation != null ? res.arabicExplanation : "").append("\n\n");

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
        return sb.toString();
    }

    public static void copyToClipboard(Context context, GoldAnalysisEngine.AnalysisResult res) {
        String text = formatAnalysisToText(res);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AWRIDI_Gold_Analysis", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "تم نسخ التحليل بالكامل ✓", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareResults(Context context, GoldAnalysisEngine.AnalysisResult res) {
        String text = formatAnalysisToText(res);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "مشاركة نتائج تحليل الذهب عبر:");
        context.startActivity(shareIntent);
    }
}
