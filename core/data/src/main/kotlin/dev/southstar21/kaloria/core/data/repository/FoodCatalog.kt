package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.network.OpenFoodFactsDataSource
import dev.southstar21.kaloria.core.network.UsdaDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import javax.inject.Inject

/** Results from the online databases. A source that failed is reported, not fatal. */
data class OnlineResults(
    val openFoodFacts: List<RemoteFood> = emptyList(),
    val usda: List<RemoteFood> = emptyList(),
    val failed: Set<FoodSource> = emptySet(),
)

sealed interface BarcodeResult {
    data class Own(val food: Food) : BarcodeResult
    data class Online(val food: RemoteFood) : BarcodeResult
    data object NotFound : BarcodeResult
    data object Offline : BarcodeResult
}

/** Searches outside the user's own database, and copies chosen foods into it. */
class FoodCatalog @Inject constructor(
    private val off: OpenFoodFactsDataSource,
    private val usda: UsdaDataSource,
    private val foods: FoodRepository,
) {
    suspend fun searchOnline(query: String): OnlineResults = coroutineScope {
        val offResult = async { runCatchingNetwork { off.search(query) } }
        val usdaResult = async { runCatchingNetwork { usda.search(query) } }
        val o = offResult.await()
        val u = usdaResult.await()
        OnlineResults(
            openFoodFacts = o.getOrDefault(emptyList()),
            usda = u.getOrDefault(emptyList()),
            failed = buildSet {
                if (o.isFailure) add(FoodSource.OpenFoodFacts)
                if (u.isFailure) add(FoodSource.Usda)
            },
        )
    }

    /** Own database first (so the user's corrections win), then Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        foods.byBarcode(barcode)?.let { return BarcodeResult.Own(it) }
        return runCatchingNetwork { off.product(barcode) }.fold(
            onSuccess = { it?.let(BarcodeResult::Online) ?: BarcodeResult.NotFound },
            onFailure = { BarcodeResult.Offline },
        )
    }

    /** The user's copy of [remote], creating it on first use. */
    suspend fun import(remote: RemoteFood): Food =
        foods.bySource(remote.source, remote.sourceRef) ?: foods.save(
            Food(
                id = Ids.newId(),
                name = remote.name,
                brand = remote.brand,
                barcode = remote.barcode,
                source = remote.source,
                sourceRef = remote.sourceRef,
                unit = remote.unit,
                nutrients = remote.nutrients,
                servings = remote.servings,
                updatedAt = Instant.now(),
            ),
        )

    private inline fun <T> runCatchingNetwork(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
