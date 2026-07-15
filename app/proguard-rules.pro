# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.oderommanager.app.data.model.** { *; }

# Keep Gson model classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep enum values (used for Room TypeConverters)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
