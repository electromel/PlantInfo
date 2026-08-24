package ch.electromel.plantinfo.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Convertisseurs Room pour les listes de chaînes (chemins de photos, recommandations). */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else json.decodeFromString(stringListSerializer, value)
}
