package com.awridi.ai;

import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class TelegramNotifier {

    public static void sendTelegramAlertIfNeeded(SharedPreferences prefs, ExecutorService executor, com.awridi.ai.GoldAnalysisEngine.AnalysisResult res) {
        String token = EncryptedPrefsHelper.getSecureString(prefs, MainActivity.PREF_KEY_TELEGRAM_TOKEN, "").trim();
        String chatId = EncryptedPrefsHelper.getSecureString(prefs, MainActivity.PREF_KEY_TELEGRAM_CHAT_ID, "").trim();

        if (token.isEmpty() || chatId.isEmpty() || res == null) return;

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
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.getResponseCode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
