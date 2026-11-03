package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.database.model.asExternalModel
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import java.time.Clock
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

    /** Inserts or updates; stamps [Food.updatedAt] with now. Returns the saved food. */
    suspend fun save(food: Food): Food
    suspend fun delete(id: String)
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

    override suspend fun byBarcode(barcode: String) = dao.byBarcode(barcode.trim())?.asExternalModel()

    override suspend fun bySource(source: FoodSource, sourceRef: String) = dao.bySourceRef(source.name, sourceRef)?.asExternalModel()

    override suspend fun save(food: Food): Food {
        val saved = food.copy(name = food.name.trim(), brand = food.brand?.trim()?.ifEmpty { null }, updatedAt = clock.instant())
        dao.upsert(saved.asEntity())
        sync.requestSync()
        return saved
    }

    override suspend fun delete(id: String) {
        dao.softDelete(id, clock.millis())
        sync.requestSync()
    }
}

/** Replaces a food's named servings, as edited from the portion sheet. */
suspend fun FoodRepository.saveServings(id: String, servings: List<dev.southstar21.kaloria.core.model.Serving>) {
    get(id)?.let { save(it.copy(servings = servings)) }
}
