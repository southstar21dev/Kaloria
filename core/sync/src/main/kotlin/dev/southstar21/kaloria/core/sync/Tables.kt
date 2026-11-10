package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.SyncDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.FoodEntity
import dev.southstar21.kaloria.core.database.model.GoalsEntity
import dev.southstar21.kaloria.core.database.model.LogEntryEntity
import dev.southstar21.kaloria.core.database.model.NutrientColumns
import dev.southstar21.kaloria.core.database.model.SyncedEntity
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Serving
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Shared plumbing for tables backed by a Room [SyncDao]. */
internal abstract class RoomTable<E : SyncedEntity>(
    override val collection: String,
    private val dao: SyncDao<E>,
) : SyncTable<E> {
    override suspend fun dirty() = dao.dirty()
    override suspend fun saveIfNewer(rows: List<E>) = if (rows.isEmpty()) 0 else dao.upsertIfNewer(rows, { it.id }, { it.updatedAt })
    override suspend fun markClean(rows: List<E>) {
        if (rows.isNotEmpty()) dao.markCleanAll(rows.associate { it.id to it.updatedAt })
    }
    override fun id(row: E) = row.id
    override fun updatedAt(row: E) = row.updatedAt

    protected fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.ifEmpty { null }
    protected fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    protected fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
    protected fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    protected fun JsonObject.obj(key: String) = this[key] as? JsonObject
    protected fun JsonObject.clientUpdatedAt() = long("clientUpdatedAt") ?: 0L

    protected fun common(row: E) = mapOf("clientUpdatedAt" to JsonPrimitive(row.updatedAt), "deleted" to JsonPrimitive(row.deleted))

    protected fun nutrientsJson(n: NutrientColumns): JsonObject = buildJsonObject {
        fun num(key: String, value: Double?) = put(key, value?.let(::JsonPrimitive) ?: JsonNull)
        num("energyKcal", n.energyKcal)
        num("protein", n.protein)
        num("carbohydrates", n.carbohydrates)
        num("sugars", n.sugars)
        num("fat", n.fat)
        num("saturatedFat", n.saturatedFat)
        num("fiber", n.fiber)
        num("salt", n.salt)
        put("micros", JsonObject(n.micros.mapValues { JsonPrimitive(it.value) }))
    }

    protected fun nutrientsFrom(json: JsonObject?): NutrientColumns {
        val j = json ?: JsonObject(emptyMap())
        return NutrientColumns(
            energyKcal = j.double("energyKcal"),
            protein = j.double("protein"),
            carbohydrates = j.double("carbohydrates"),
            sugars = j.double("sugars"),
            fat = j.double("fat"),
            saturatedFat = j.double("saturatedFat"),
            fiber = j.double("fiber"),
            salt = j.double("salt"),
            micros = j.obj("micros").orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }.toMap(),
        )
    }

    protected inline fun <reified T : Enum<T>> JsonObject.enum(key: String, default: T): T = str(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
}

internal class FoodsTable(dao: FoodDao) : RoomTable<FoodEntity>("foods", dao) {
    override fun toJson(row: FoodEntity) = JsonObject(
        mapOf(
            "name" to JsonPrimitive(row.name),
            "brand" to JsonPrimitive(row.brand.orEmpty()),
            "barcode" to JsonPrimitive(row.barcode.orEmpty()),
            "source" to JsonPrimitive(row.source.name),
            "sourceRef" to JsonPrimitive(row.sourceRef.orEmpty()),
            "unit" to JsonPrimitive(row.unit.name),
            "servings" to buildJsonArray {
                row.servings.forEach {
                    add(
                        buildJsonObject {
                            put("name", it.name)
                            put("amount", it.amount)
                        },
                    )
                }
            },
            "favorite" to JsonPrimitive(row.favorite),
            "nutrients" to nutrientsJson(row.nutrients),
        ) + common(row),
    )

    override fun fromJson(json: JsonObject): FoodEntity? = FoodEntity(
        id = json.str("id") ?: return null,
        name = json.str("name") ?: return null,
        brand = json.str("brand"),
        barcode = json.str("barcode"),
        source = json.enum("source", FoodSource.Manual),
        sourceRef = json.str("sourceRef"),
        unit = json.enum("unit", AmountUnit.Gram),
        nutrients = nutrientsFrom(json.obj("nutrients")),
        servings = (json["servings"] as? JsonArray).orEmpty().mapNotNull { element ->
            val s = element as? JsonObject ?: return@mapNotNull null
            Serving(s.str("name") ?: return@mapNotNull null, s.double("amount") ?: return@mapNotNull null)
        },
        favorite = json.bool("favorite"),
        updatedAt = json.clientUpdatedAt(),
        deleted = json.bool("deleted"),
        dirty = false,
    )

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

internal class LogEntriesTable(dao: LogEntryDao) : RoomTable<LogEntryEntity>("log_entries", dao) {
    override fun toJson(row: LogEntryEntity) = JsonObject(
        mapOf(
            "foodId" to JsonPrimitive(row.foodId.orEmpty()),
            "foodName" to JsonPrimitive(row.foodName),
            "brand" to JsonPrimitive(row.brand.orEmpty()),
            "amount" to JsonPrimitive(row.amount),
            "unit" to JsonPrimitive(row.unit.name),
            "loggedAt" to JsonPrimitive(row.loggedAt),
            "note" to JsonPrimitive(row.note.orEmpty()),
            "nutrientsPer100" to nutrientsJson(row.nutrientsPer100),
        ) + common(row),
    )

    override fun fromJson(json: JsonObject): LogEntryEntity? = LogEntryEntity(
        id = json.str("id") ?: return null,
        foodId = json.str("foodId"),
        foodName = json.str("foodName") ?: return null,
        brand = json.str("brand"),
        amount = json.double("amount") ?: return null,
        unit = json.enum("unit", AmountUnit.Gram),
        nutrientsPer100 = nutrientsFrom(json.obj("nutrientsPer100")),
        loggedAt = json.long("loggedAt") ?: return null,
        note = json.str("note"),
        updatedAt = json.clientUpdatedAt(),
        deleted = json.bool("deleted"),
        dirty = false,
    )
}

internal class WeightTable(dao: WeightDao) : RoomTable<WeightEntryEntity>("weight_entries", dao) {
    override fun toJson(row: WeightEntryEntity) = JsonObject(
        mapOf(
            "kilograms" to JsonPrimitive(row.kilograms),
            "loggedAt" to JsonPrimitive(row.loggedAt),
            "healthConnectId" to JsonPrimitive(row.healthConnectId.orEmpty()),
        ) + common(row),
    )

    override fun fromJson(json: JsonObject): WeightEntryEntity? = WeightEntryEntity(
        id = json.str("id") ?: return null,
        kilograms = json.double("kilograms") ?: return null,
        loggedAt = json.long("loggedAt") ?: return null,
        healthConnectId = json.str("healthConnectId"),
        updatedAt = json.clientUpdatedAt(),
        deleted = json.bool("deleted"),
        dirty = false,
    )
}

internal class GoalsTable(dao: GoalsDao) : RoomTable<GoalsEntity>("goals", dao) {
    override fun toJson(row: GoalsEntity) = JsonObject(
        mapOf(
            "targets" to buildJsonObject {
                fun int(key: String, value: Int?) = put(key, value?.let(::JsonPrimitive) ?: JsonNull)
                int("energyKcal", row.energyKcal)
                int("protein", row.protein)
                int("carbohydrates", row.carbohydrates)
                int("fat", row.fat)
                int("fiber", row.fiber)
                int("steps", row.steps)
                put("micros", JsonObject(row.micros.mapValues { JsonPrimitive(it.value) }))
            },
        ) + common(row),
    )

    override fun fromJson(json: JsonObject): GoalsEntity? {
        val t = json.obj("targets") ?: JsonObject(emptyMap())
        fun int(key: String) = t.long(key)?.toInt()
        return GoalsEntity(
            id = json.str("id") ?: return null,
            energyKcal = int("energyKcal"),
            protein = int("protein"),
            carbohydrates = int("carbohydrates"),
            fat = int("fat"),
            fiber = int("fiber"),
            steps = int("steps"),
            micros = t.obj("micros")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }?.toMap().orEmpty(),
            updatedAt = json.clientUpdatedAt(),
            deleted = json.bool("deleted"),
            dirty = false,
        )
    }
}
