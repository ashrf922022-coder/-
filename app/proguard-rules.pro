# R8 / ProGuard Security Rules for AWRIDI AI Gold

# Code Obfuscation & Shrinking Settings
-repackageclasses 'com.awridi.ai.internal'
-allowaccessmodification
-dontwarn **

# Preserve Android Entry Points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Data Models and Data Inner Classes for JSON parsing
-keep class com.awridi.ai.GoldAnalysisEngine$Bar { *; }
-keep class com.awridi.ai.GoldAnalysisEngine$AnalysisResult { *; }
-keep class com.awridi.ai.PaperTradingManager$PaperTrade { *; }
-keep class com.awridi.ai.BacktestEngine$BacktestResult { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve line numbers for stack traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,*Annotation*
