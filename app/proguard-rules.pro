-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn eu.agno3.jcifs.**
-dontwarn sun.misc.**
-dontwarn androidx.room.**
-dontwarn androidx.security.crypto.**
-dontwarn io.github.agrevster.pocketbasekotlin.**
-dontwarn kotlin.reflect.jvm.internal.**

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes SourceFile
-keepattributes LineNumberTable

-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }

# ── Serialization ─────────────────────────────────────────────
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class com.example.ritik_2.** { *; }
-keep class com.example.ritik_2.data.model.** { *; }
-keep class com.example.ritik_2.data.source.dto.** { *; }
-keepclassmembers class com.example.ritik_2.data.model.** { *; }
-keepclassmembers class com.example.ritik_2.data.source.dto.** { *; }
-keepclassmembers class com.example.ritik_2.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ritik_2.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Hilt / DI ─────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# ── OkHttp (keep public API only) ────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Ktor ──────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# ── PocketBase SDK ────────────────────────────────────────────
-keep class io.github.agrevster.pocketbasekotlin.** { *; }
-keepclassmembers class io.github.agrevster.pocketbasekotlin.** { *; }

# ── DTO classes ───────────────────────────────────────────────
-keep class com.example.ritik_2.data.source.dto.UserRecord { *; }
-keep class com.example.ritik_2.data.source.dto.AccessControlRecord { *; }
-keep class com.example.ritik_2.data.source.dto.CompanyRecord { *; }
-keep class com.example.ritik_2.data.source.dto.SearchIndexRecord { *; }

# ── SMB / Logging ─────────────────────────────────────────────
-keep class eu.agno3.jcifs.** { *; }
-keepclassmembers class eu.agno3.jcifs.** { *; }
-keep class org.slf4j.** { *; }

# ── Gson ──────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Coil ──────────────────────────────────────────────────────
-keep class coil.** { *; }

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# ── Security Crypto ───────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Timber ────────────────────────────────────────────────────
-keep class timber.log.Timber { *; }

# ── Retrofit (Nagios) ────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── Exception classes ─────────────────────────────────────────
-keep public class * extends java.lang.Exception

# ── WebView JS bridge ────────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}



# Missing - Rules

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid