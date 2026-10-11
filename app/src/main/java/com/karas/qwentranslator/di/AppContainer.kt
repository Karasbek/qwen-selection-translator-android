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
    val apiUsage = com.karas.qwentranslator.core.ApiUsageTracker()
    val anki: com.karas.qwentranslator.anki.AnkiGateway = com.karas.qwentranslator.anki.AndroidAnkiGateway(context)
    val settings = SettingsRepository(context.applicationContext)
    val secrets = SecretStore(context.applicationContext)
    val ankiSaver = com.karas.qwentranslator.anki.AnkiSaveCoordinator(anki, settings,
        com.karas.qwentranslator.anki.FileSaveReceiptStore(context.applicationContext))

    private val httpClient = OkHttpClient.Builder()
        .eventListener(object : okhttp3.EventListener() {
            override fun requestHeadersStart(call: okhttp3.Call) {
                call.request().tag(com.karas.qwentranslator.core.ApiOperation::class.java)?.let(apiUsage::httpAttempt)
            }
        })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    val pronunciations: com.karas.qwentranslator.anki.PronunciationRepository =
        com.karas.qwentranslator.anki.OfflinePronunciationRepository {
            context.applicationContext.assets.open("ipa/en_US.txt").use { it.readBytes() }
        }

    val engine: TranslatorEngine = DefaultTranslatorEngine(
        providers = listOf(
            OpenAiCompatibleProvider(httpClient, settings, secrets, apiUsage),
            GoogleWebTranslateProvider(httpClient),
        ),
    )
}
