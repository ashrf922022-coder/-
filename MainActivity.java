package com.tarhal.ai;
import android.app.*; import android.os.*; import android.webkit.*; import android.content.*;
public class MainActivity extends Activity {
 WebView w;
 @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(com.tarhal.ai.R.layout.activity_main);
  w=findViewById(com.tarhal.ai.R.id.webview); w.getSettings().setJavaScriptEnabled(true); w.getSettings().setDomStorageEnabled(true);
  w.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,String url){
   if(url.startsWith("https://wa.me/")||url.startsWith("whatsapp://")){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));return true;} return false;}});
  // بعد الاستضافة: استبدل الرابط التالي برابط نظام ترحال HTTPS
  w.loadUrl("https://YOUR-TARHAL-DOMAIN.example/");
 }
 @Override public void onBackPressed(){if(w.canGoBack())w.goBack();else super.onBackPressed();}
}