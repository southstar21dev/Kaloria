package dev.southstar21.kaloria.core.database

import androidx.room.TypeConverter
import dev.southstar21.kaloria.core.model.Serving
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ServingJson(val name: String, val amount: Double)

internal class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun microsToJson(value: Map<String, Double>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToMicros(value: String): Map<String, Double> = json.decodeFromString(value)

    @TypeConverter
    fun servingsToJson(value: List<Serving>): String = json.encodeToString(value.map { ServingJson(it.name, it.amount) })

    @TypeConverter
    fun jsonToServings(value: String): List<Serving> = json.decodeFromString<List<ServingJson>>(value).map { Serving(it.name, it.amount) }
}
