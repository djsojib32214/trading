# ---------------------------------------------------------------
# ApexTerminal R8 configuration
# ---------------------------------------------------------------

# Keep every @JavascriptInterface member: the WebView bridge resolves
# these reflectively by exact name and would break under obfuscation.
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.apex.terminal.ui.chart.ChartBridge { *; }

# Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Room entities / DAO generated code
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# Gson DTOs (reflection based (de)serialization)
-keep class com.apex.terminal.data.remote.dto.** { *; }
-keep class com.apex.terminal.domain.model.** { *; }
-keep class com.apex.terminal.data.local.entity.** { *; }

# Kotlin metadata used by Gson / Retrofit reflection
-keep class kotlin.Metadata { *; }

# Silent release-only warnings for optional platform classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
