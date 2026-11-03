package dev.southstar21.kaloria.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.model.suspendRunCatching
import dev.southstar21.kaloria.core.network.OpenFoodFactsDataSource
import dev.southstar21.kaloria.core.network.UsdaDataSource
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** The phone has no internet connection. */
    data object Offline : BarcodeResult

    /** Online, but Open Food Facts didn't answer even after retrying. */
    data object Unavailable : BarcodeResult
}

/** Searches outside the user's own database, and copies chosen foods into it. */
@Singleton
class FoodCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val off: OpenFoodFactsDataSource,
    private val usda: UsdaDataSource,
    private val foods: FoodRepository,
    private val clock: Clock,
) {
    /** One import at a time, so tapping the same result twice can't create two copies. */
    private val importing = Mutex()

    suspend fun searchOnline(query: String): OnlineResults = coroutineScope {
        val offResult = async { suspendRunCatching { off.search(query) } }
        val usdaResult = async { suspendRunCatching { usda.search(query) } }
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

    /** Open Food Facts answers this session (the catalog lives as long as the app), so scanning the same product again is instant. */
    private val recent = object : LinkedHashMap<String, BarcodeResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BarcodeResult>) = size > 64
    }

    /** Own database first (so the user's corrections win), then Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        foods.byBarcode(barcode)?.let { return BarcodeResult.Own(it) }
        synchronized(recent) { recent[barcode] }?.let { return it }
        return suspendRunCatching { off.product(barcode) }.fold(
            onSuccess = { found ->
                (found?.let(BarcodeResult::Online) ?: BarcodeResult.NotFound).also { synchronized(recent) { recent[barcode] = it } }
            },
            // Only say "offline" when the phone really is; otherwise it was Open Food Facts.
            onFailure = { if (isOnline()) BarcodeResult.Unavailable else BarcodeResult.Offline },
        )
    }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** The user's copy of [remote], creating it on first use. */
    suspend fun import(remote: RemoteFood): Food = importing.withLock {
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
                updatedAt = clock.instant(),
            ),
        )
    }
}
