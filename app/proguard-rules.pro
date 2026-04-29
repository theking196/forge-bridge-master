# ProGuard rules for Forge Bridge
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep model classes for serialization
-keep class com.forge.bridge.data.remote.providers.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class com.forge.bridge.**$$serializer { *; }
-keepclassmembers class com.forge.bridge.** {
    *** Companion;
}
-keepclasseswithmembers class com.forge.bridge.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Ktor / Netty
-dontwarn io.netty.**
-keep class io.netty.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
