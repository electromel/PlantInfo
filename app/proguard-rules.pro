# Règles ProGuard/R8 par défaut. Le build release actuel ne minifie pas (isMinifyEnabled = false),
# mais on conserve les annotations de sérialisation kotlinx au cas où la minification serait activée.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.plantinfo.**$$serializer { *; }
-keepclassmembers class com.plantinfo.** {
    *** Companion;
}
-keepclasseswithmembers class com.plantinfo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
