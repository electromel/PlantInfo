# Règles R8 de la version publiée (isMinifyEnabled = true, voir build.gradle.kts).
# Les bibliothèques (Room, Hilt, WorkManager, CameraX, Coil, osmdroid…) livrent leurs propres règles ;
# on ne garde ici que ce qui est propre à l'application.

-keepattributes *Annotation*, InnerClasses

# kotlinx.serialization : les sérialiseurs générés sont retrouvés par nom.
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class ch.electromel.plantinfo.**$$serializer { *; }
-keepclassmembers class ch.electromel.plantinfo.** {
    *** Companion;
}
-keepclasseswithmembers class ch.electromel.plantinfo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager enregistre le NOM de la classe du worker avec chaque identification mise en file hors-ligne.
# Si R8 le renommait, une file créée par la version précédente (non obfusquée) ne retrouverait plus son
# worker après la mise à jour et serait abandonnée en silence.
-keepnames class ch.electromel.plantinfo.work.IdentificationWorker
