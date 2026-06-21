# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.yapyap.**$$serializer { *; }
-keepclassmembers class com.yapyap.** {
    *** Companion;
}
-keepclasseswithmembers class com.yapyap.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Koin
-keep class org.koin.** { *; }
-keep class com.yapyap.** { *; }
