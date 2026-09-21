package com.awridi.ai;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TelegramNotifier {

    public interface TelegramCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public static void sendTelegramMessageAsync(String botToken, String chatId, String messageText, TelegramCallback callback) {
        if (botToken == null || botToken.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            if (callback != null) callback.onError("بيانات Telegram Bot Token أو Chat ID مفقودة");
            return;
        }

        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + URLEncoder.encode(botToken, "UTF-8")
                        + "/sendMessage?chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                        + "&parse_mode=Markdown"
                        + "&text=" + URLEncoder.encode(messageText, "UTF-8");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    if (callback != null) callback.onSuccess();
                } else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    if (callback != null) callback.onError("خطأ في الخادم Telegram: " + code + " " + sb.toString());
                }
            } catch (Exception e) {
                if (callback != null) callback.onError("فشل الاتصال بـ Telegram: " + e.getMessage());
            }
        }).start();
    }
}
