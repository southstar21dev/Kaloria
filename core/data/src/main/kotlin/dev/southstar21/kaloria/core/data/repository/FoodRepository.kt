package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.search
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.database.model.asExternalModel
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Serving
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface FoodRepository {
    fun search(query: String): Flow<List<Food>>
    fun observe(id: String): Flow<Food?>
    suspend fun get(id: String): Food?
    suspend fun byBarcode(barcode: String): Food?

    /** An already-imported Open Food Facts / USDA food, so it isn't copied twice. */
    suspend fun bySource(source: FoodSource, sourceRef: String): Food?

    /**
     * Inserts or updates; stamps [Food.updatedAt] with now (or just after the stored copy, if the
     * clock is behind it). Text longer than the server accepts is cut to fit. Returns the saved food.
     */
    suspend fun save(food: Food): Food
    suspend fun delete(id: String)

    /** Replaces a food's named servings, leaving the rest of it as stored. */
    suspend fun updateServings(id: String, servings: List<Serving>) {
        get(id)?.let { save(it.copy(servings = servings)) }
    }
}

internal class OfflineFirstFoodRepository @Inject constructor(
    private val dao: FoodDao,
    private val clock: Clock,
    private val sync: SyncRequester,
) : FoodRepository {
    // Mapped off the main thread: a search emits up to 200 foods on every keystroke.
    override fun search(query: String) = dao.search(query.trim()).map { list -> list.map { it.asExternalModel() } }
        .flowOn(Dispatchers.Default)

    override fun observe(id: String) = dao.observe(id).map { it?.asExternalModel() }

    override suspend fun get(id: String) = dao.get(id)?.takeUnless { it.deleted }?.asExternalModel()

    // Cut like a saved barcode, so a code longer than the server allows still finds its food.
    override suspend fun byBarcode(barcode: String) = dao.byBarcode(barcode.trim().clip(ServerLimits.BARCODE))?.asExternalModel()

    override suspend fun bySource(source: FoodSource, sourceRef: String) = dao.bySourceRef(source.name, sourceRef.clip(ServerLimits.SOURCE_REF))?.asExternalModel()

    override suspend fun save(food: Food): Food {
        val fitted = food.copy(
            name = food.name.trim().clip(ServerLimits.NAME).trimEnd(),
            brand = food.brand?.trim()?.clip(ServerLimits.BRAND)?.trimEnd()?.ifEmpty { null },
            barcode = food.barcode?.clip(ServerLimits.BARCODE),
            sourceRef = food.sourceRef?.clip(ServerLimits.SOURCE_REF),
            updatedAt = clock.instant(),
        )
        val stored = dao.save(fitted.asEntity())
        sync.requestSync()
        return fitted.copy(updatedAt = Instant.ofEpochMilli(stored.updatedAt))
    }

    override suspend fun delete(id: String) {
        dao.softDelete(id, clock.millis())
        sync.requestSync()
    }

    // Only the servings column: a sync or edit that landed since the food was read is kept.
    override suspend fun updateServings(id: String, servings: List<Serving>) {
        if (dao.updateServings(id, servings, clock.millis()) > 0) sync.requestSync()
    }
}

/** Replaces a food's named servings, as edited from the portion sheet. */
suspend fun FoodRepository.saveServings(id: String, servings: List<Serving>) = updateServings(id, servings)
