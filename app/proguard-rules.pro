# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to rules specified
# in /app/build.gradle. You can have all rules for your application
# in this file, or create a new file and reference it
# using the proguardFiles directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Général : signatures génériques et annotations (Retrofit / Gson) ---
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# --- Retrofit : méthodes annotées des interfaces d'API ---
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Gson : modèles désérialisés par réflexion (noms de champs JSON = champs Kotlin) ---
-keep class com.meteojules.model.** { <fields>; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-dontwarn sun.misc.**
