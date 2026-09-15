package com.iamskorpz.watchioiptv.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.iamskorpz.watchioiptv.BuildConfig
import com.iamskorpz.watchioiptv.core.logging.WatchioLogger
import com.iamskorpz.watchioiptv.core.security.SensitiveUrlMasker
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(maskingDiagnosticsInterceptor())
        .build()

    fun retrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun maskingDiagnosticsInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (BuildConfig.DEBUG) {
            WatchioLogger.debug(
                "Network",
                "HTTP ${request.method} ${SensitiveUrlMasker.mask(request.url.toString())}",
            )
        }
        chain.proceed(request)
    }
}
