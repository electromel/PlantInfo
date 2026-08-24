# Règles ProGuard/R8 par défaut. Le build release actuel ne minifie pas (isMinifyEnabled = false),
# mais on conserve les annotations de sérialisation kotlinx au cas où la minification serait activée.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class ch.electromel.plantinfo.**$$serializer { *; }
-keepclassmembers class ch.electromel.plantinfo.** {
    *** Companion;
}
-keepclasseswithmembers class ch.electromel.plantinfo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
