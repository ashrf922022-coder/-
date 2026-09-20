
package com.awridi.ai;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    LinearLayout root, content, nav;
    SharedPreferences prefs;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    JSONObject design, strat;

    int primary, secondary, background, surface, textColor, muted;
    EditText apiKey, symbols;
    TextView title, status, results;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("awridi_v3",MODE_PRIVATE);
        design=load("design", "design_default.json");
        strat=load("strategy", "strategy_default.json");
        applyTheme();
        showHome();
    }

    JSONObject load(String key, String asset) {
        try {
            String saved=prefs.getString(key,null);
            if(saved!=null) return new JSONObject(saved);
            InputStream in=getAssets().open(asset);
            BufferedReader r=new BufferedReader(new InputStreamReader(in));
            StringBuilder s=new StringBuilder(); String l;
            while((l=r.readLine())!=null)s.append(l);
            return new JSONObject(s.toString());
        } catch(Exception e) { return new JSONObject(); }
    }
    void save(String key, JSONObject o){ prefs.edit().putString(key,o.toString()).apply(); }

    void applyTheme(){
        primary=Color.parseColor(design.optString("primary","#6C8CFF"));
        secondary=Color.parseColor(design.optString("secondary","#9AA7FF"));
        background=Color.parseColor(design.optString("background","#0A0F1F"));
        surface=Color.parseColor(design.optString("surface","#121A33"));
        textColor=Color.parseColor(design.optString("text","#F5F7FF"));
        muted=Color.parseColor(design.optString("muted","#AAB4D0"));
    }

    TextView tv(String s,int sp){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(textColor); t.setTextSize(sp);
        t.setPadding(0,8,0,8); return t;
    }
    Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(primary); return b;
    }
    LinearLayout box(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(18,18,18,18); l.setBackgroundColor(surface);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,12,0,0); l.setLayoutParams(p);
        return l;
    }
    EditText input(String hint,String value){
        EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(textColor); e.setHintTextColor(muted);
        return e;
    }

    void base(String page){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(background);
        LinearLayout head=new LinearLayout(this); head.setPadding(18,20,18,10); head.setGravity(Gravity.CENTER_VERTICAL);
        title=tv(design.optString("appName","AWRIDI AI"),25); title.setTypeface(null,1); head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView v=tv("v3",13); v.setTextColor(secondary); head.addView(v);
        root.addView(head);
        ScrollView sc=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,0,18,18); sc.addView(content); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setPadding(6,4,6,4); nav.setGravity(Gravity.CENTER);
        Button h=btn("الرئيسية"); h.setOnClickListener(vv->showHome());
        Button bt=btn("Backtest"); bt.setOnClickListener(vv->showBacktest());
        Button ds=btn("التصميم"); ds.setOnClickListener(vv->showDesign());
        nav.addView(h,new LinearLayout.LayoutParams(0,-2,1)); nav.addView(bt,new LinearLayout.LayoutParams(0,-2,1)); nav.addView(ds,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(nav);
        setContentView(root);
    }

    void showHome(){
        base("home");
        LinearLayout hero=box(); hero.addView(tv("محرك ذكاء السوق",20)); hero.addView(tv("تحليل + Backtesting + إعدادات قابلة للتخصيص",14));
        content.addView(hero);
        LinearLayout cfg=box(); cfg.addView(tv("اتصال البيانات",18));
        apiKey=input("Twelve Data API Key",prefs.getString("key","")); cfg.addView(apiKey);
        symbols=input("الأسهم مفصولة بفاصلة","AAPL,MSFT,NVDA,AMZN,GOOGL,META,TSLA"); cfg.addView(symbols);
        Button save=btn("حفظ"); save.setOnClickListener(v->{prefs.edit().putString("key",apiKey.getText().toString().trim()).apply(); status.setText("تم الحفظ.");}); cfg.addView(save);
        Button scan=btn("🔎 فحص السوق الآن"); scan.setOnClickListener(v->scan()); cfg.addView(scan);
        status=tv("أدخل مفتاح Twelve Data ثم ابدأ الفحص.",14); status.setTextColor(muted); cfg.addView(status);
        results=tv("لا توجد نتائج بعد.",14); results.setTextColor(textColor); content.addView(cfg); content.addView(results);

        if(design.optBoolean("showStrategy",true)){
            LinearLayout s=box(); s.addView(tv("الاستراتيجية النشطة",18));
            s.addView(tv("SMA "+strat.optInt("fastSma",10)+" / "+strat.optInt("slowSma",30)+
                         " • RSI "+strat.optInt("rsiPeriod",14)+" ["+strat.optDouble("rsiMin",55)+"–"+strat.optDouble("rsiMax",75)+"]"+
                         " • ATR Stop "+strat.optDouble("atrStop",2)+"x / Target "+strat.optDouble("atrTarget",3)+"x",14));
            Button edit=btn("تعديل الاستراتيجية"); edit.setOnClickListener(v->showStrategy()); s.addView(edit);
            content.addView(s);
        }
    }

    void scan(){
        String key=apiKey.getText().toString().trim();
        if(key.isEmpty()){status.setText("ضع مفتاح API أولًا.");return;}
        status.setText("جاري فحص الأسهم...");
        final String[] syms=symbols.getText().toString().replace(" ","").toUpperCase().split(",");
        executor.submit(()->{
            ArrayList<String> rows=new ArrayList<>();
            for(String sym:syms) try{
                if(sym.trim().isEmpty()) continue;
                List<Bar> b=fetch(sym,key,Math.min(120,strat.optInt("maxBars",500)));
                Metrics m=metrics(b);
                rows.add("📌 "+sym+"\nالسعر "+f(m.last)+"  •  SMA"+strat.optInt("fastSma",10)+" "+f(m.fast)+"  •  SMA"+strat.optInt("slowSma",30)+" "+f(m.slow)+"\nRSI "+f(m.rsi)+"  •  MACD Hist "+f(m.macdHist)+"  •  ATR "+f(m.atr)+"\nAWRIDI Score: "+f(m.score)+"  →  "+(m.score>=0.7?"توافق قوي":m.score>=0.5?"مراقبة":"لا توجد إشارة كافية"));
            } catch(Exception e){rows.add("❌ "+sym+"\n"+e.getMessage());}
            runOnUiThread(()->{status.setText("اكتمل الفحص: "+rows.size()+" رموز."); showResults(rows);});
        });
    }

    void showResults(List<String> rows){
        if(results==null) return;
        results.setText("");
        for(String row:rows){
            TextView card=tv(row,14);
            card.setPadding(18,18,18,18);
            card.setBackgroundColor(surface);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,0);
            content.addView(card, content.indexOfChild(results), lp);
        }
    }

    void showBacktest(){
        base("backtest");
        LinearLayout b=box(); b.addView(tv("🧪 Backtesting",21));
        b.addView(tv("اختبر الاستراتيجية على تاريخ السهم دون إرسال أي أمر للسوق.",14));
        EditText sym=input("رمز السهم","AAPL"); b.addView(sym);
        Button run=btn("تشغيل الاختبار"); b.addView(run);
        TextView out=tv("النتائج ستظهر هنا.",14); b.addView(out); content.addView(b);
        run.setOnClickListener(v->{String key=prefs.getString("key","");if(key.isEmpty()){out.setText("أدخل API Key من الرئيسية.");return;}
            out.setText("جاري تشغيل الاختبار...");
            executor.submit(()->{
                try{List<Bar> bars=fetch(sym.getText().toString().trim().toUpperCase(),key,strat.optInt("maxBars",500));
                    BT r=backtest(bars);
                    String res="الرمز: "+sym.getText().toString().toUpperCase()+"\n"+
                    "الصفقات: "+r.trades+"\n"+
                    "نسبة الفوز: "+pct(r.winRate)+"\n"+
                    "العائد: "+pct(r.returnPct)+"\n"+
                    "Max Drawdown: "+pct(r.maxDD)+"\n"+
                    "Profit Factor: "+f(r.profitFactor)+"\n"+
                    "رأس المال النهائي: "+f(r.finalCapital)+"\n\n"+
                    "هذه أرقام تاريخية للاختبار وليست ضمانًا للمستقبل.";
                    runOnUiThread(()->out.setText(res));
                }catch(Exception e){runOnUiThread(()->out.setText("خطأ: "+e.getMessage()));}
            });
        });
    }

    void showStrategy(){
        base("strategy");
        LinearLayout b=box(); b.addView(tv("⚙️ محرّك الاستراتيجية",21));
        String[] labels={"SMA السريع","SMA البطيء","RSI period","RSI أدنى","RSI أعلى","ATR period","Stop ATR","Target ATR","Volume multiplier","رأس المال","المخاطرة %","Max bars"};
        String[] keys={"fastSma","slowSma","rsiPeriod","rsiMin","rsiMax","atrPeriod","atrStop","atrTarget","volumeMultiplier","initialCapital","riskPerTrade","maxBars"};
        for(int i=0;i<labels.length;i++){EditText e=input(labels[i]," "+strat.optString(keys[i],"")); e.setTag(keys[i]); b.addView(e);}
        Button save=btn("حفظ الاستراتيجية"); b.addView(save); content.addView(b);
        save.setOnClickListener(v->{
            for(int i=0;i<keys.length;i++){EditText e=(EditText)b.getChildAt(i+1);String val=e.getText().toString().trim(); if(!val.isEmpty()){try{if(i==0||i==1||i==2||i==5||i==11)strat.put(keys[i],Integer.parseInt(val));else strat.put(keys[i],Double.parseDouble(val));}catch(Exception ex){}}}
            save("strategy",strat); Toast.makeText(this,"تم حفظ إعدادات الاستراتيجية.",Toast.LENGTH_SHORT).show(); showHome();
        });
    }

    void showDesign(){
        base("design");
        LinearLayout b=box(); b.addView(tv("🎨 استوديو التصميم",21));
        String[] labels={"اسم التطبيق","اللون الرئيسي #","اللون الثانوي #","الخلفية #","البطاقات #","لون النص #","لون النص الثانوي #","زاوية البطاقات"};
        String[] keys={"appName","primary","secondary","background","surface","text","muted","cardRadius"};
        for(int i=0;i<labels.length;i++){EditText e=input(labels[i],design.optString(keys[i],"")); b.addView(e);}
        Switch scan=new Switch(this);scan.setText("إظهار فحص السوق");scan.setTextColor(textColor);scan.setChecked(design.optBoolean("showScan",true));b.addView(scan);
        Switch bt=new Switch(this);bt.setText("إظهار Backtest");bt.setTextColor(textColor);bt.setChecked(design.optBoolean("showBacktest",true));b.addView(bt);
        Switch st=new Switch(this);st.setText("إظهار الاستراتيجية");st.setTextColor(textColor);st.setChecked(design.optBoolean("showStrategy",true));b.addView(st);
        Button save=btn("تطبيق وحفظ التصميم"); b.addView(save); content.addView(b);
        save.setOnClickListener(v->{
            for(int i=0;i<keys.length;i++){EditText e=(EditText)b.getChildAt(i+1);String val=e.getText().toString().trim(); if(!val.isEmpty()){try{design.put(keys[i],i==7?Integer.parseInt(val):val);}catch(Exception ex){}}}
            design.put("showScan",scan.isChecked());design.put("showBacktest",bt.isChecked());design.put("showStrategy",st.isChecked());
            save("design",design);applyTheme();showHome();
        });
    }

    static class Bar { double o,h,l,c,v; Bar(double o,double h,double l,double c,double v){this.o=o;this.h=h;this.l=l;this.c=c;this.v=v;} }
    List<Bar> fetch(String sym,String key,int n)throws Exception{
        String url="https://api.twelvedata.com/time_series?symbol="+URLEncoder.encode(sym,"UTF-8")+"&interval=1day&outputsize="+n+"&apikey="+URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(15000);c.setReadTimeout(20000);
        BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);r.close();
        JSONObject j=new JSONObject(s.toString());if(!j.has("values"))throw new Exception(j.optString("message","No data"));
        JSONArray a=j.getJSONArray("values");List<Bar> out=new ArrayList<>();
        for(int i=a.length()-1;i>=0;i--){JSONObject x=a.getJSONObject(i);out.add(new Bar(x.getDouble("open"),x.getDouble("high"),x.getDouble("low"),x.getDouble("close"),x.optDouble("volume",0)));}
        return out;
    }

    static class Metrics {double last,fast,slow,rsi,macdHist,atr,score;}
    Metrics metrics(List<Bar>b){
        Metrics m=new Metrics(); int n=b.size(); m.last=b.get(n-1).c;
        int f=stratInt("fastSma",10), s=stratInt("slowSma",30), rp=stratInt("rsiPeriod",14), ap=stratInt("atrPeriod",14);
        m.fast=sma(b,n-1,f);m.slow=sma(b,n-1,s);m.rsi=rsi(b,rp,n-1);m.macdHist=macdHist(b,n-1);m.atr=atr(b,ap,n-1);
        double t=m.fast>m.slow?1:0, r=(m.rsi>=stratDouble("rsiMin",55)&&m.rsi<=stratDouble("rsiMax",75))?1:0, q=m.macdHist>0?1:0;
        m.score=.40*t+.30*r+.30*q;return m;
    }
    int stratInt(String k,int d){return strat.optInt(k,d);}
    double stratDouble(String k,double d){return strat.optDouble(k,d);}
    double sma(List<Bar>b,int end,int len){if(end<0)return 0;int st=Math.max(0,end-len+1);double x=0;for(int i=st;i<=end;i++)x+=b.get(i).c;return x/(end-st+1);}
    double rsi(List<Bar>b,int p,int end){int st=Math.max(1,end-p+1);double g=0,l=0;for(int i=st;i<=end;i++){double d=b.get(i).c-b.get(i-1).c;if(d>=0)g+=d;else l-=d;}if(l==0)return 100;int cnt=Math.max(1,end-st+1); double rs=(g/cnt)/(l/cnt);return 100-100/(1+rs);}
    double atr(List<Bar>b,int p,int end){int st=Math.max(1,end-p+1);double x=0;for(int i=st;i<=end;i++){Bar z=b.get(i);double pc=b.get(i-1).c;x+=Math.max(z.h-z.l,Math.max(Math.abs(z.h-pc),Math.abs(z.l-pc)));}return x/Math.max(1,end-st+1);}
    double ema(List<Double>x,int p,int end){if(end<0)return 0;double k=2.0/(p+1),e=x.get(0);for(int i=1;i<=end;i++)e=x.get(i)*k+e*(1-k);return e;}
    double macdHist(List<Bar>b,int end){List<Double>x=new ArrayList<>();for(Bar z:b)x.add(z.c);int start=Math.max(0,end-60);List<Double> y=x.subList(start,end+1);int eidx=y.size()-1;double mac=ema(y,12,eidx)-ema(y,26,eidx);List<Double> macs=new ArrayList<>();for(int i=0;i<y.size();i++){double ee=Math.min(i,y.size()-1);macs.add(ema(y,12,(int)ee)-ema(y,26,(int)ee));}double sig=ema(macs,9,macs.size()-1);return mac-sig;}

    static class BT{int trades,wins;double returnPct,maxDD,profitFactor,finalCapital,winRate;}
    BT backtest(List<Bar>b){
        BT z=new BT();double cash=stratDouble("initialCapital",10000), start=cash,equity=cash,peak=cash,profit=0,loss=0;
        boolean in=false;double entry=0,stop=0,target=0,qty=0;
        int fast=stratInt("fastSma",10), slow=stratInt("slowSma",30), rp=stratInt("rsiPeriod",14), ap=stratInt("atrPeriod",14);
        for(int i=Math.max(slow,ap)+2;i<b.size();i++){
            Bar cur=b.get(i);double smaF=sma(b,i,fast),smaS=sma(b,i,slow),rv=rsi(b,rp,i), av=atr(b,ap,i);
            double prevVol=0;int vs=Math.max(0,i-20);for(int k=vs;k<i;k++)prevVol+=b.get(k).v;prevVol=Math.max(1,prevVol/(i-vs));
            if(!in && smaF>smaS && rv>=stratDouble("rsiMin",55)&&rv<=stratDouble("rsiMax",75)&&cur.v>=prevVol*stratDouble("volumeMultiplier",1)){
                double risk=cash*stratDouble("riskPerTrade",1)/100.0;entry=cur.c;stop=entry-av*stratDouble("atrStop",2);target=entry+av*stratDouble("atrTarget",3);
                double per=Math.max(0.01,entry-stop);qty=risk/per;qty=Math.min(qty,cash/entry);cash-=qty*entry;in=true;z.trades++;
            } else if(in){
                double exit=Double.NaN;
                if(cur.l<=stop)exit=stop; else if(cur.h>=target)exit=target; else if(smaF<smaS)exit=cur.c;
                if(!Double.isNaN(exit)){double pnl=(exit-entry)*qty;cash+=qty*exit;equity=cash; if(pnl>=0){profit+=pnl;z.wins++;}else loss-=pnl;in=false;}
            }
            equity=in?cash+qty*cur.c:cash;peak=Math.max(peak,equity);z.maxDD=Math.max(z.maxDD,(peak-equity)/peak);
        }
        if(in){double e=b.get(b.size()-1).c;cash+=qty*e;equity=cash;double pnl=(e-entry)*qty;if(pnl>=0){profit+=pnl;z.wins++;}else loss-=pnl;}
        z.finalCapital=cash;z.returnPct=(cash/start)-1;z.maxDD=-z.maxDD;z.winRate=z.trades>0?(double)z.wins/z.trades:0;z.profitFactor=loss>0?profit/loss:profit>0?999:0;return z;
    }
    String f(double x){return String.format(Locale.US,"%.2f",x);}
    String pct(double x){return String.format(Locale.US,"%.2f%%",x*100);}
}
