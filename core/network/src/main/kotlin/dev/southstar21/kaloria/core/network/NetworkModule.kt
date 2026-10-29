package dev.southstar21.kaloria.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

internal val NetworkJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/** Open Food Facts asks every client to identify itself. */
internal const val USER_AGENT = "Kaloria/0.1 (Android; personal use)"

internal fun HttpClient.configured() = config {
    install(ContentNegotiation) { json(NetworkJson) }
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
    }
    expectSuccess = true
}

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp).configured()

    @Provides
    @UsdaApiKey
    fun usdaApiKey(): String = BuildConfig.USDA_API_KEY
}

@javax.inject.Qualifier
annotation class UsdaApiKey
