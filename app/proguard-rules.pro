-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn eu.agno3.jcifs.**
-dontwarn sun.misc.**
-dontwarn androidx.room.**
-dontwarn androidx.compose.**
-dontwarn androidx.security.crypto.**
-dontwarn coil.**
-dontwarn io.github.agrevster.pocketbasekotlin.**
-dontwarn kotlin.reflect.jvm.internal.**

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes SourceFile
-keepattributes LineNumberTable

-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

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

-keep class com.example.ritik_2.BuildConfig { *; }

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

-keep class io.github.agrevster.pocketbasekotlin.** { *; }
-keepclassmembers class io.github.agrevster.pocketbasekotlin.** { *; }

-keep class com.example.ritik_2.data.source.dto.UserRecord { *; }
-keep class com.example.ritik_2.data.source.dto.AccessControlRecord { *; }
-keep class com.example.ritik_2.data.source.dto.CompanyRecord { *; }
-keep class com.example.ritik_2.data.source.dto.SearchIndexRecord { *; }

-keep class eu.agno3.jcifs.** { *; }
-keepclassmembers class eu.agno3.jcifs.** { *; }
-keep class org.slf4j.** { *; }

-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keep class coil.** { *; }

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

-keep class androidx.compose.** { *; }
-keep class androidx.security.crypto.** { *; }

-keep public class * extends java.lang.Exception

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}