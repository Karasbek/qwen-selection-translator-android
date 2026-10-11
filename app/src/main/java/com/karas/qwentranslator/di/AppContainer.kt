package com.karas.qwentranslator.di

import android.content.Context
import com.karas.qwentranslator.core.DefaultTranslatorEngine
import com.karas.qwentranslator.core.TranslatorEngine
import com.karas.qwentranslator.data.SecretStore
import com.karas.qwentranslator.data.SettingsRepository
import com.karas.qwentranslator.network.GoogleWebTranslateProvider
import com.karas.qwentranslator.network.OpenAiCompatibleProvider
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val anki = com.karas.qwentranslator.anki.AndroidAnkiGateway(context)
    val settings = SettingsRepository(context.applicationContext)
    val secrets = SecretStore(context.applicationContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val engine: TranslatorEngine = DefaultTranslatorEngine(
        providers = listOf(
            OpenAiCompatibleProvider(httpClient, settings, secrets),
            GoogleWebTranslateProvider(httpClient),
        ),
    )
}
