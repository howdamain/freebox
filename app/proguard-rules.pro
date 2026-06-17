# --- kotlinx.serialization (required so @Serializable DTOs survive R8) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.freebox.app.**$$serializer { *; }
-keepclassmembers class com.freebox.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.freebox.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep our data-layer model/DTO classes (decoded reflectively by Postgrest)
-keep class com.freebox.app.data.** { *; }

# --- Ktor client + coroutines (Supabase transport) ---
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Supabase SDK ---
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Compose / AndroidX ship their own consumer rules; nothing extra needed.
