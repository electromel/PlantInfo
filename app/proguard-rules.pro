# Règles R8 propres à l'application, en plus de celles que chaque bibliothèque embarque (Room, Hilt,
# OkHttp, WorkManager, kotlinx.serialization). Le build release est minifié : toute règle ajoutée ici
# se vérifie sur la variante `minified` installée sur un appareil (voir CLAUDE.md).
#
# Sérialisation kotlinx : les sérialiseurs générés des DTO sont atteints par réflexion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class ch.electromel.plantinfo.**$$serializer { *; }
-keepclassmembers class ch.electromel.plantinfo.** {
    *** Companion;
}
-keepclasseswithmembers class ch.electromel.plantinfo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
