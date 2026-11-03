package dev.southstar21.kaloria.core.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor

internal val NetworkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Open Food Facts asks every client to identify itself. */
internal const val USER_AGENT = "Kaloria/0.1 (Android; personal use)"

internal fun HttpClientConfig<*>.kaloriaDefaults() {
    install(ContentNegotiation) { json(NetworkJson) }
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
        requestTimeoutMillis = 20_000
    }
    expectSuccess = true
}

/** Food databases change slowly: let their answers be reused for a day, even if they don't say so. */
private val CACHEABLE_HOSTS = setOf("world.openfoodfacts.org", "search.openfoodfacts.org")

private val cacheFoodResponses = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    val request = chain.request()
    if (request.method == "GET" && request.url.host in CACHEABLE_HOSTS && response.isSuccessful && response.header("Cache-Control") == null) {
        response.newBuilder().header("Cache-Control", "public, max-age=86400").build()
    } else {
        response
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(@ApplicationContext context: Context): HttpClient = HttpClient(OkHttp) {
        kaloriaDefaults()
        engine {
            config {
                cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
                addNetworkInterceptor(cacheFoodResponses)
            }
        }
    }

    @Provides
    @UsdaApiKey
    fun usdaApiKey(): String = BuildConfig.USDA_API_KEY
}

@javax.inject.Qualifier
annotation class UsdaApiKey
